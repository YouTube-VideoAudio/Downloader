package com.videoaudio.downloader;

import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadService extends Service {
    interface Listener { void onDone(boolean ok, String file, String error); }
    static volatile Listener listener;

    private static final String CH_PROGRESS = "dl_progress";
    private static final String CH_DONE = "dl_done";
    private static final int ONGOING_ID = 1;

    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private int pending = 0;
    private int doneCounter = 100;
    private PowerManager.WakeLock wake;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createChannels();
        final String id = intent != null ? intent.getStringExtra("id") : null;
        final String format = intent != null ? intent.getStringExtra("format") : null;
        final String url = intent != null ? intent.getStringExtra("url") : null;
        synchronized (this) { pending++; }
        if (Build.VERSION.SDK_INT >= 29) startForeground(ONGOING_ID, ongoing(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(ONGOING_ID, ongoing());
        if (wake == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "videodownloader:dl");
            wake.acquire(60L * 60L * 1000L);
        }
        if (!YtHelper.validId(id) || format == null || YtHelper.isBlocked(url)) {
            finishOne(null, "Invalid request");
            return START_NOT_STICKY;
        }
        pool.execute(() -> {
            String file = null;
            String err = null;
            try {
                file = YtHelper.download(getApplicationContext(), id, url, format);
            } catch (Exception e) {
                err = YtHelper.shortErr(e);
                android.util.Log.e("YtDlp", "download failed", e);
            }
            finishOne(file, err);
        });
        return START_NOT_STICKY;
    }

    private Notification.Builder builder(String channel) {
        return Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, channel) : new Notification.Builder(this);
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CH_PROGRESS, "Download progress", NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel(CH_DONE, "Download finished", NotificationManager.IMPORTANCE_DEFAULT));
    }

    private synchronized Notification ongoing() {
        String text = pending <= 1 ? "Downloading... you can leave the app" : pending + " downloads in progress";
        return builder(CH_PROGRESS)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Video Downloader")
                .setContentText(text)
                .setOngoing(true)
                .setProgress(0, 0, true)
                .build();
    }

    private void finishOne(String file, String err) {
        boolean ok = file != null;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Intent open = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String text = ok ? "Saved to Downloads/Video Downloader: " + file : (err == null ? "Unknown error" : err);
        Notification n = builder(CH_DONE)
                .setSmallIcon(ok ? android.R.drawable.stat_sys_download_done : android.R.drawable.stat_notify_error)
                .setContentTitle(ok ? "Download complete" : "Download failed")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        int nid;
        boolean last;
        synchronized (this) {
            nid = doneCounter++;
            pending--;
            last = pending <= 0;
        }
        nm.notify(nid, n);
        Listener l = listener;
        if (l != null) l.onDone(ok, file, err);
        if (last) {
            if (wake != null && wake.isHeld()) wake.release();
            wake = null;
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
            stopSelf();
        } else {
            nm.notify(ONGOING_ID, ongoing());
        }
    }

    @Override
    public void onDestroy() {
        pool.shutdown();
        if (wake != null && wake.isHeld()) wake.release();
        super.onDestroy();
    }
}
