package com.videoaudio.downloader;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

final class YtHelper {
    private static boolean ready = false;
    private static boolean updated = false;
    private static String updNote = "";

    private YtHelper() {}

    static synchronized void ensureInit(Context ctx) throws Exception {
        if (ready) return;
        Context app = ctx.getApplicationContext();
        YoutubeDL.getInstance().init(app);
        FFmpeg.getInstance().init(app);
        updateYtDlp(app);
        ready = true;
    }

    private static void updateYtDlp(Context app) {
        if (updated) return;
        updated = true;
        try {
            Class<?> ch = Class.forName("com.yausername.youtubedl_android.YoutubeDL$UpdateChannel");
            Object stable = null;
            try { stable = ch.getField("_STABLE").get(null); } catch (Throwable t) {}
            if (stable == null) {
                Class<?> st = Class.forName("com.yausername.youtubedl_android.YoutubeDL$UpdateChannel$STABLE");
                stable = st.getField("INSTANCE").get(null);
            }
            YoutubeDL y = YoutubeDL.getInstance();
            Object r = y.getClass().getMethod("updateYoutubeDL", Context.class, ch).invoke(y, app, stable);
            updNote = "upd:" + r;
        } catch (Throwable t) {
            updNote = "upd-fail:" + t.getClass().getSimpleName();
            android.util.Log.e("YtDlp", "update failed", t);
        }
    }

    static boolean validId(String id) {
        return id != null && id.matches("[\\w-]{11}");
    }

    static String shortErr(Exception e) {
        String msg = String.valueOf(e.getMessage());
        StringBuilder sb = new StringBuilder();
        for (String line : msg.split("\\n")) {
            if (line.startsWith("ERROR")) sb.append(line.trim()).append(' ');
        }
        String r;
        if (sb.length() > 0) {
            r = sb.toString();
            if (r.length() > 300) r = r.substring(0, 300);
        } else {
            r = msg.length() > 300 ? msg.substring(msg.length() - 300) : msg;
        }
        return r + " [" + updNote + "]";
    }

    static String download(Context ctx, String id, String format) throws Exception {
        if (Build.VERSION.SDK_INT < 29) throw new Exception("Android 10 or newer is required.");
        ensureInit(ctx);
        final File dir = new File(ctx.getCacheDir(), "dl");
        dir.mkdirs();
        final String prefix = "v" + System.currentTimeMillis() + ".";
        try {
            YoutubeDLRequest req = new YoutubeDLRequest("https://www.youtube.com/watch?v=" + id);
            req.addOption("--no-playlist");
            req.addOption("--force-ipv4");
            req.addOption("--retries", "3");
            req.addOption("--fragment-retries", "3");
            req.addOption("--socket-timeout", "20");
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
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Video Downloader");
            ContentResolver r = ctx.getContentResolver();
            Uri u = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (u == null) throw new Exception("save failed");
            try (OutputStream os = r.openOutputStream(u); InputStream is = new FileInputStream(f)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
            }
            return outName;
        } finally {
            File[] left = dir.listFiles((d, nm) -> nm.startsWith(prefix));
            if (left != null) for (File x : left) x.delete();
        }
    }
}
