package com.videoaudio.downloader;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.mapper.VideoInfo;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

@CapacitorPlugin(name = "YtDlp")
public class YtDlpPlugin extends Plugin {
    private boolean ready = false;

    private synchronized void ensureInit() throws Exception {
        if (ready) return;
        YoutubeDL.getInstance().init(getContext());
        FFmpeg.getInstance().init(getContext());
        ready = true;
    }

    private static boolean validId(String id) {
        return id != null && id.matches("[\\w-]{11}");
    }

    private static JSObject fmt(String label, String value) {
        JSObject f = new JSObject();
        f.put("label", label);
        f.put("value", value);
        return f;
    }

    @PluginMethod
    public void info(final PluginCall call) {
        final String id = call.getString("id");
        if (!validId(id)) { call.reject("Invalid link"); return; }
        new Thread(() -> {
            try {
                ensureInit();
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
        if (!validId(id) || format == null || !format.matches("mp4_(1080|720|480|360)|mp3")) {
            call.reject("Invalid request");
            return;
        }
        if (Build.VERSION.SDK_INT < 29) {
            call.reject("Android 10 or newer is required.");
            return;
        }
        new Thread(() -> {
            final File dir = new File(getContext().getCacheDir(), "dl");
            dir.mkdirs();
            final String prefix = "v" + System.currentTimeMillis() + ".";
            try {
                ensureInit();
                YoutubeDLRequest req = new YoutubeDLRequest("https://www.youtube.com/watch?v=" + id);
                req.addOption("--no-playlist");
                req.addOption("-o", new File(dir, prefix + "%(ext)s").getAbsolutePath());
                if (format.equals("mp3")) {
                    req.addOption("-x");
                    req.addOption("--audio-format", "mp3");
                    req.addOption("--audio-quality", "0");
                } else {
                    String h = format.substring(4);
                    req.addOption("-f", "bv*[height<=" + h + "][vcodec^=avc1]+ba[ext=m4a]/bv*[height<=" + h + "]+ba/b[height<=" + h + "]");
                    req.addOption("--merge-output-format", "mp4");
                }
                YoutubeDL.getInstance().execute(req, "dl-" + prefix, false, null);

                File[] fs = dir.listFiles((d, nm) -> nm.startsWith(prefix));
                if (fs == null || fs.length == 0) throw new Exception("no file");
                File f = fs[0];
                String ext = f.getName().substring(f.getName().lastIndexOf('.') + 1);
                String outName = "video-" + id + "." + ext;

                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, outName);
                v.put(MediaStore.MediaColumns.MIME_TYPE, ext.equals("mp3") ? "audio/mpeg" : "video/mp4");
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                ContentResolver r = getContext().getContentResolver();
                Uri u = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (u == null) throw new Exception("save failed");
                try (OutputStream os = r.openOutputStream(u); InputStream is = new FileInputStream(f)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
                }
                JSObject out = new JSObject();
                out.put("file", outName);
                call.resolve(out);
            } catch (Exception e) {
                android.util.Log.e("YtDlp", "download failed", e);
                String m = String.valueOf(e.getMessage());
                if (m.length() > 350) m = m.substring(0, 350);
                call.reject("Download failed: " + m);
            } finally {
                File[] left = dir.listFiles((d, nm) -> nm.startsWith(prefix));
                if (left != null) for (File x : left) x.delete();
            }
        }).start();
    }
}
