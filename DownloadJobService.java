package com.ashraful.fastdownload;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PersistableBundle;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadJobService extends JobService {
    private static final String CHANNEL = "downloads";
    private static final Map<Integer, DownloadEngine> RUNNING = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override public boolean onStartJob(JobParameters params) {
        long id = params.getExtras().getLong("download_id", -1);
        DownloadItem item = DownloadStore.get(this, id);
        if (item == null) { jobFinished(params, false); return false; }
        DownloadStore.init(this);
        createChannel();
        if (Build.VERSION.SDK_INT >= 34) setNotification(params, (int)(id % 100000 + 2000), buildNotification(item, 0), JOB_END_NOTIFICATION_POLICY_REMOVE);
        DownloadEngine engine = new DownloadEngine(this, item, params.getNetwork());
        RUNNING.put(params.getJobId(), engine);
        executor.execute(() -> engine.run(new DownloadEngine.Callback() {
            private void n(long done, long total, long bps) { if (Build.VERSION.SDK_INT >= 34) setNotification(params, (int)(id % 100000 + 2000), buildNotification(item, total <= 0 ? 0 : (int)Math.min(100, done * 100 / total)), JOB_END_NOTIFICATION_POLICY_REMOVE); }
            @Override public void onProgress(long done, long total, long bps) { n(done, total, bps); }
            @Override public void onSuccess() { RUNNING.remove(params.getJobId()); jobFinished(params, false); }
            @Override public void onPaused() { RUNNING.remove(params.getJobId()); jobFinished(params, false); }
            @Override public void onError(String message) { RUNNING.remove(params.getJobId()); jobFinished(params, false); }
        }));
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        DownloadEngine e = RUNNING.remove(params.getJobId());
        if (e != null) e.requestStop();
        DownloadItem item = DownloadStore.get(this, params.getExtras().getLong("download_id", -1));
        return item != null && DownloadItem.DOWNLOADING.equals(item.status);
    }

    private Notification buildNotification(DownloadItem item, int pct) {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, (int)(item.id & 0x7fffffff), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setSmallIcon(com.ashraful.fastdownload.R.drawable.ic_download).setContentTitle(item.name).setContentText("Downloading…")
         .setContentIntent(pi).setOngoing(true).setOnlyAlertOnce(true);
        if (pct > 0) b.setProgress(100, pct, false); else b.setProgress(0,0,true);
        return b.build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW));
        }
    }
}
