package com.videoaudio.downloader;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.mapper.VideoInfo;

@CapacitorPlugin(name = "YtDlp")
public class YtDlpPlugin extends Plugin {

    private static JSObject fmt(String label, String value) {
        JSObject f = new JSObject();
        f.put("label", label);
        f.put("value", value);
        return f;
    }

    @PluginMethod
    public void info(final PluginCall call) {
        final String id = call.getString("id");
        if (!YtHelper.validId(id)) { call.reject("Invalid link"); return; }
        new Thread(() -> {
            try {
                YtHelper.ensureInit(getContext());
                VideoInfo vi = YoutubeDL.getInstance().getInfo("https://www.youtube.com/watch?v=" + id);
                JSObject out = new JSObject();
                out.put("title", vi.getTitle());
                JSArray formats = new JSArray();
                int[] hs = {1080, 720, 480, 360};
                for (int h : hs) formats.put(fmt("MP4 - " + h + "p", "mp4_" + h));
                formats.put(fmt("MP3 - Audio only", "mp3"));
                out.put("formats", formats);
                call.resolve(out);
            } catch (Exception e) {
                call.reject("Could not fetch this video.");
            }
        }).start();
    }

    @PluginMethod
    public void download(final PluginCall call) {
        final String id = call.getString("id");
        final String format = call.getString("format");
        final String title = call.getString("title");
        if (!YtHelper.validId(id) || format == null || !format.matches("mp4_(1080|720|480|360)|mp3")) {
            call.reject("Invalid request");
            return;
        }
        if (Build.VERSION.SDK_INT < 29) {
            call.reject("Android 10 or newer is required.");
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && getActivity() != null) {
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7001);
        }
        DownloadService.listener = (ok, file, err) -> {
            JSObject o = new JSObject();
            o.put("ok", ok);
            o.put("file", file);
            o.put("error", err);
            notifyListeners("downloadDone", o);
        };
        try {
            Intent i = new Intent(getContext(), DownloadService.class);
            i.putExtra("id", id);
            i.putExtra("format", format);
            i.putExtra("title", title);
            ContextCompat.startForegroundService(getContext(), i);
            JSObject r = new JSObject();
            r.put("started", true);
            call.resolve(r);
        } catch (Exception e) {
            call.reject("Could not start the download.");
        }
    }

    @PluginMethod
    public void permStatus(PluginCall call) {
        android.content.Context c = getContext();
        boolean notif = androidx.core.app.NotificationManagerCompat.from(c).areNotificationsEnabled();
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notif = false;
        }
        boolean batt = true;
        try {
            android.os.PowerManager pm = (android.os.PowerManager) c.getSystemService(android.content.Context.POWER_SERVICE);
            batt = pm.isIgnoringBatteryOptimizations(c.getPackageName());
        } catch (Exception e) {}
        JSObject o = new JSObject();
        o.put("notifications", notif);
        o.put("battery", batt);
        call.resolve(o);
    }

    @PluginMethod
    public void requestNotifications(PluginCall call) {
        boolean settings = Boolean.TRUE.equals(call.getBoolean("settings", false));
        android.content.Context c = getContext();
        try {
            if (!settings && Build.VERSION.SDK_INT >= 33
                    && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    && getActivity() != null) {
                ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7001);
            } else {
                Intent i = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                i.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, c.getPackageName());
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
            }
            call.resolve();
        } catch (Exception e) {
            call.reject("Could not open the permission screen.");
        }
    }

    @PluginMethod
    public void requestBattery(PluginCall call) {
        android.content.Context c = getContext();
        try {
            Intent i = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    android.net.Uri.parse("package:" + c.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            call.resolve();
        } catch (Exception e) {
            try {
                Intent i2 = new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                i2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i2);
                call.resolve();
            } catch (Exception e2) {
                call.reject("Could not open the battery screen.");
            }
        }
    }

    @PluginMethod
    public void listDownloads(final PluginCall call) {
        if (android.os.Build.VERSION.SDK_INT < 29) { call.reject("Android 10 or newer is required."); return; }
        new Thread(() -> {
            try {
                android.content.ContentResolver r = getContext().getContentResolver();
                String[] proj = {
                    android.provider.MediaStore.MediaColumns._ID,
                    android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                    android.provider.MediaStore.MediaColumns.SIZE,
                    android.provider.MediaStore.MediaColumns.MIME_TYPE,
                    android.provider.MediaStore.MediaColumns.DATE_ADDED
                };
                String sel = android.provider.MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
                String[] args = { android.os.Environment.DIRECTORY_DOWNLOADS + "/Video Downloader%" };
                JSArray arr = new JSArray();
                try (android.database.Cursor c = r.query(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, proj, sel, args,
                        android.provider.MediaStore.MediaColumns.DATE_ADDED + " DESC")) {
                    if (c != null) {
                        while (c.moveToNext()) {
                            long id = c.getLong(0);
                            String mime = c.getString(3);
                            JSObject o = new JSObject();
                            o.put("uri", android.content.ContentUris.withAppendedId(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, id).toString());
                            o.put("name", c.getString(1));
                            o.put("size", c.getLong(2));
                            o.put("mime", mime);
                            o.put("audio", mime != null && mime.startsWith("audio"));
                            o.put("date", c.getLong(4) * 1000L);
                            arr.put(o);
                        }
                    }
                }
                JSObject out = new JSObject();
                out.put("files", arr);
                call.resolve(out);
            } catch (Exception e) {
                call.reject("Could not list downloads.");
            }
        }).start();
    }

    @PluginMethod
    public void play(PluginCall call) {
        String uri = call.getString("uri");
        String title = call.getString("title", "Video");
        String base = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI.toString();
        if (uri == null || !uri.startsWith(base)) { call.reject("Invalid file"); return; }
        try {
            android.content.Intent i = new android.content.Intent(getContext(), PlayerActivity.class);
            i.putExtra("uri", uri);
            i.putExtra("title", title);
            if (getActivity() != null) {
                getActivity().startActivity(i);
            } else {
                i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(i);
            }
            call.resolve();
        } catch (Exception e) {
            call.reject("Could not open the player.");
        }
    }

    @PluginMethod
    public void deleteDownload(PluginCall call) {
        String uri = call.getString("uri");
        String base = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI.toString();
        if (uri == null || !uri.startsWith(base)) { call.reject("Invalid file"); return; }
        try {
            int n = getContext().getContentResolver().delete(android.net.Uri.parse(uri), null, null);
            if (n > 0) call.resolve(); else call.reject("Could not delete this file.");
        } catch (Exception e) {
            call.reject("Could not delete this file.");
        }
    }
}
