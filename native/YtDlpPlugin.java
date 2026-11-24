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
}
