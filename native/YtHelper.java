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

    static String download(Context ctx, String id, String url, String format) throws Exception {
        if (Build.VERSION.SDK_INT < 29) throw new Exception("Android 10 or newer is required.");
        ensureInit(ctx);
        final File dir = new File(ctx.getCacheDir(), "dl");
        dir.mkdirs();
        final String prefix = "v" + System.currentTimeMillis() + ".";
        try {
            String target = target(id, url);
            if (target == null) throw new Exception("Invalid link");
            YoutubeDLRequest req = new YoutubeDLRequest(target);
            req.addOption("--no-playlist");
            req.addOption("--playlist-items", "1");
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
                req.addOption("-f", "bv*[height<=" + h + "][vcodec^=avc1]+ba[ext=m4a]/bv*[height<=" + h + "]+ba/b[height<=" + h + "]/b");
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
            v.put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(ext));
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

    static String search(Context ctx, String query, int n) throws Exception {
        ensureInit(ctx);
        YoutubeDLRequest req = new YoutubeDLRequest("ytsearch" + n + ":" + query);
        req.addOption("--flat-playlist");
        req.addOption("--dump-single-json");
        req.addOption("--no-warnings");
        req.addOption("--force-ipv4");
        req.addOption("--retries", "2");
        req.addOption("--socket-timeout", "20");
        com.yausername.youtubedl_android.YoutubeDLResponse resp =
                YoutubeDL.getInstance().execute(req, "s-" + System.currentTimeMillis(), false, null);
        return resp.getOut();
    }

    private static final String[] BLOCKED = {
        "chaturbate.com","stripchat.com","onlyfans.com","fansly.com",
        "netflix.com","primevideo.com","disneyplus.com","hulu.com","max.com","hbomax.com","peacocktv.com",
        "paramountplus.com","crunchyroll.com","hotstar.com","jiocinema.com","sonyliv.com","zee5.com",
        "spotify.com","music.apple.com","tv.apple.com","tidal.com","deezer.com"
    };

    static boolean isBlocked(String url) {
        if (url == null || url.trim().isEmpty()) return false;
        String host;
        try { host = new java.net.URL(url.trim()).getHost(); } catch (Exception e) { return false; }
        if (host == null) return false;
        host = host.toLowerCase(java.util.Locale.ROOT);
        for (String d : BLOCKED) {
            if (host.equals(d) || host.endsWith("." + d)) return true;
        }
        return false;
    }

    static String target(String id, String url) {
        if (url != null && !url.trim().isEmpty()) {
            String u = url.trim();
            if (u.length() > 2000 || !u.matches("(?i)^https?://\\S+$")) return null;
            return u;
        }
        return validId(id) ? "https://www.youtube.com/watch?v=" + id : null;
    }

    static String mimeFor(String ext) {
        String e = ext == null ? "" : ext.toLowerCase(java.util.Locale.ROOT);
        switch (e) {
            case "mp3": return "audio/mpeg";
            case "m4a": return "audio/mp4";
            case "opus": case "ogg": return "audio/ogg";
            case "webm": return "video/webm";
            case "mkv": return "video/x-matroska";
            default: return "video/mp4";
        }
    }
}
