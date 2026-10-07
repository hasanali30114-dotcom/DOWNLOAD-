package com.ashraful.fastdownload;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadForegroundService extends Service {
    private static final String CHANNEL = "downloads_legacy";
    private static final Map<Long, DownloadEngine> RUNNING = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override public void onCreate() { super.onCreate(); createChannel(); startForeground(1, baseNotification("Preparing download…")); }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        long id = intent == null ? -1 : intent.getLongExtra("download_id", -1);
        if (id < 0) { stopIfIdle(); return START_NOT_STICKY; }
        DownloadItem item = DownloadStore.get(this, id);
        if (item == null) { stopIfIdle(); return START_NOT_STICKY; }
        DownloadEngine engine = new DownloadEngine(this, item);
        RUNNING.put(id, engine);
        executor.execute(() -> engine.run(new DownloadEngine.Callback() {
            private void n(long done, long total) { int pct = total > 0 ? (int)Math.min(100, done * 100 / total) : 0; Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(DownloadForegroundService.this, CHANNEL) : new Notification.Builder(DownloadForegroundService.this); b.setSmallIcon(R.drawable.ic_download).setContentTitle(item.name).setContentText(pct + "%").setOngoing(true).setOnlyAlertOnce(true).setProgress(100,pct,total<=0); ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify((int)(id & 0x7fffffff), b.build()); }
            @Override public void onProgress(long done, long total, long bps) { n(done,total); }
            @Override public void onSuccess() { RUNNING.remove(id); stopIfIdle(); }
            @Override public void onPaused() { RUNNING.remove(id); stopIfIdle(); }
            @Override public void onError(String m) { RUNNING.remove(id); stopIfIdle(); }
        }));
        return START_NOT_STICKY;
    }

    @Override public void onTimeout(int startId, int fgsType) {
        for (DownloadEngine e : RUNNING.values()) e.requestStop();
        RUNNING.clear();
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true);
        stopSelf();
    }

    @Override public void onDestroy() {
        for (DownloadEngine e : RUNNING.values()) e.requestStop();
        RUNNING.clear(); executor.shutdownNow(); super.onDestroy();
    }

    private synchronized void stopIfIdle() { if (RUNNING.isEmpty()) { if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true); stopSelf(); } }
    private Notification baseNotification(String txt) {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 9001, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return b.setSmallIcon(R.drawable.ic_download).setContentTitle("ASHRAFUL DOWNLOAD MANAGER").setContentText(txt).setContentIntent(pi).setOngoing(true).build();
    }
    private void createChannel() { if (Build.VERSION.SDK_INT >= 26) { NotificationManager nm = getSystemService(NotificationManager.class); if (nm != null) nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW)); } }
    @Override public IBinder onBind(Intent intent) { return null; }
}
