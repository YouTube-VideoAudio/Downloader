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
        updateYtDlp();
        ready = true;
    }

    private static boolean updated = false;
    private static String updNote = "";

    private void updateYtDlp() {
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
            Object r = y.getClass().getMethod("updateYoutubeDL", android.content.Context.class, ch).invoke(y, getContext(), stable);
            updNote = "upd:" + r;
        } catch (Throwable t) {
            updNote = "upd-fail:" + t.getClass().getSimpleName();
            android.util.Log.e("YtDlp", "update failed", t);
        }
    }

    private static String shortErr(Exception e) {
        call.reject("Download failed: " + shortErr(e) + " [" + updNote + "]");
            } finally {
                File[] left = dir.listFiles((d, nm) -> nm.startsWith(prefix));
                if (left != null) for (File x : left) x.delete();
            }
        }).start();
    }
}
