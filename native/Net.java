package com.videoaudio.downloader;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

final class Net {
    private Net() {}

    static String get(String url, int timeoutMs) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "VideoDownloader");
            int code = c.getResponseCode();
            if (code != 200) throw new Exception("HTTP " + code);
            try (InputStream is = c.getInputStream(); ByteArrayOutputStream bo = new ByteArrayOutputStream()) {
                byte[] buf = new byte[4096];
                int n;
                int total = 0;
                while ((n = is.read(buf)) > 0 && total < 65536) {
                    bo.write(buf, 0, n);
                    total += n;
                }
                return bo.toString("UTF-8");
            }
        } finally {
            c.disconnect();
        }
    }

    static String redirectTarget(String url, int timeoutMs) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "VideoDownloader");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                if (loc != null) return loc;
            }
            throw new Exception("HTTP " + code);
        } finally {
            c.disconnect();
        }
    }
}
