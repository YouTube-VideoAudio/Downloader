package com.videoaudio.downloader;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import java.io.File;
import java.util.Locale;

final class LegacyFiles {
    private LegacyFiles() {}

    static File dir() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Video Downloader");
    }

    static boolean isOurs(String uri) {
        if (uri == null || !uri.startsWith("file://")) return false;
        try {
            String path = new File(Uri.parse(uri).getPath()).getCanonicalPath();
            return path.startsWith(dir().getCanonicalPath() + File.separator);
        } catch (Exception e) {
            return false;
        }
    }

    static JSObject list() {
        JSArray arr = new JSArray();
        File[] fs = dir().listFiles();
        if (fs != null) {
            java.util.Arrays.sort(fs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (File f : fs) {
                if (!f.isFile()) continue;
                String n = f.getName().toLowerCase(Locale.ROOT);
                boolean audio = n.endsWith(".mp3") || n.endsWith(".m4a") || n.endsWith(".opus") || n.endsWith(".ogg");
                JSObject o = new JSObject();
                o.put("uri", Uri.fromFile(f).toString());
                o.put("name", f.getName());
                o.put("size", f.length());
                o.put("mime", audio ? "audio/*" : "video/*");
                o.put("audio", audio);
                o.put("date", f.lastModified());
                arr.put(o);
            }
        }
        JSObject out = new JSObject();
        out.put("files", arr);
        return out;
    }

    static int delete(Context ctx, String uri) {
        File f = new File(Uri.parse(uri).getPath());
        boolean ok = f.delete();
        if (ok) android.media.MediaScannerConnection.scanFile(ctx, new String[]{f.getAbsolutePath()}, null, null);
        return ok ? 1 : 0;
    }
}
