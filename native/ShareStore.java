package com.videoaudio.downloader;

final class ShareStore {
    static volatile String pending;

    private ShareStore() {}

    static String take() {
        String t = pending;
        pending = null;
        return t;
    }
}
