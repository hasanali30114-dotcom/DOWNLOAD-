package com.ashraful.fastdownload;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.net.Network;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Locale;

public final class DownloadEngine {
    public interface Callback {
        void onProgress(long done, long total, long bytesPerSecond);
        void onSuccess();
        void onPaused();
        void onError(String message);
    }

    private final Context context;
    private final DownloadItem item;
    private final Network network;
    private volatile boolean stop;

    public DownloadEngine(Context c, DownloadItem item) { this(c, item, null); }
    public DownloadEngine(Context c, DownloadItem item, Network network) { this.context = c.getApplicationContext(); this.item = item; this.network = network; }
    public void requestStop() { stop = true; }

    public void run(Callback cb) {
        HttpURLConnection conn = null;
        try {
            Uri outUri = Uri.parse(item.uri);
            long existing = currentSize(outUri);
            item.downloaded = existing;
            item.status = DownloadItem.DOWNLOADING;
            DownloadStore.upsert(context, item);

            URL url = new URL(item.url);
            conn = (HttpURLConnection) (network != null ? network.openConnection(url) : url.openConnection());
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setUseCaches(false);
            conn.setRequestProperty("User-Agent", "AshrafulFastDownloadManager/1.0");
            conn.setRequestProperty("Accept-Encoding", "identity");
            if (existing > 0) conn.setRequestProperty("Range", "bytes=" + existing + "-");
            conn.connect();
            int code = conn.getResponseCode();
            if (code < 200 || code >= 400) throw new Exception("HTTP " + code);

            long total = totalLength(conn, existing, code);
            boolean append = existing > 0 && code == HttpURLConnection.HTTP_PARTIAL;
            if (existing > 0 && !append) existing = 0;
            item.downloaded = existing;
            item.total = total;
            DownloadStore.upsert(context, item);

            OutputStream rawOut = openOutput(outUri, append);
            try (InputStream in = new BufferedInputStream(conn.getInputStream(), 64 * 1024);
                 OutputStream out = new BufferedOutputStream(rawOut, 64 * 1024)) {
                byte[] buf = new byte[64 * 1024];
                long done = existing;
                long windowBytes = 0;
                long windowStart = System.nanoTime();
                int n;
                while ((n = in.read(buf)) != -1) {
                    if (stop) { item.downloaded = done; item.status = DownloadItem.PAUSED; item.message = "Paused"; DownloadStore.upsert(context, item); cb.onPaused(); return; }
                    out.write(buf, 0, n);
                    done += n;
                    windowBytes += n;
                    long now = System.nanoTime();
                    if (now - windowStart >= 500_000_000L) {
                        long bps = (long) (windowBytes * 1_000_000_000d / (now - windowStart));
                        item.downloaded = done;
                        DownloadStore.upsert(context, item);
                        cb.onProgress(done, total, bps);
                        windowBytes = 0;
                        windowStart = now;
                    }
                }
                out.flush();
                item.downloaded = done;
            }
            publish(outUri);
            item.total = item.total > 0 ? item.total : item.downloaded;
            item.status = DownloadItem.COMPLETED;
            item.message = "Completed";
            DownloadStore.upsert(context, item);
            cb.onSuccess();
        } catch (Exception e) {
            if (stop) {
                item.status = DownloadItem.PAUSED;
                item.message = "Paused";
                DownloadStore.upsert(context, item);
                cb.onPaused();
            } else {
                item.status = DownloadItem.FAILED;
                item.message = e.getMessage() == null ? "Download failed" : e.getMessage();
                DownloadStore.upsert(context, item);
                cb.onError(item.message);
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private long totalLength(HttpURLConnection conn, long existing, int code) {
        String cr = conn.getHeaderField("Content-Range");
        if (cr != null) {
            int slash = cr.lastIndexOf('/');
            if (slash >= 0) try { return Long.parseLong(cr.substring(slash + 1).trim()); } catch (Exception ignored) {}
        }
        long len = conn.getContentLengthLong();
        return len >= 0 ? len + (code == HttpURLConnection.HTTP_PARTIAL ? existing : 0) : -1;
    }

    private long currentSize(Uri uri) {
        if (Build.VERSION.SDK_INT >= 29 && uri.toString().startsWith("content://")) {
            try (android.os.ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r")) {
                return pfd == null ? 0 : pfd.getStatSize();
            } catch (Exception ignored) { return 0; }
        }
        try { return new File(uri.getPath()).length(); } catch (Exception e) { return 0; }
    }

    private OutputStream openOutput(Uri uri, boolean append) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver r = context.getContentResolver();
            return r.openOutputStream(uri, append ? "wa" : "wt");
        }
        File f = new File(uri.getPath());
        f.getParentFile().mkdirs();
        return new FileOutputStream(f, append);
    }

    private void publish(Uri uri) {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.IS_PENDING, 0);
                context.getContentResolver().update(uri, v, null, null);
            } catch (Exception ignored) {}
        }
    }

    public static Uri createDestination(Context c, String name, String mime) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, name);
            v.put(MediaStore.Downloads.MIME_TYPE, mime);
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Ashraful Download Manager");
            v.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri uri = c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new Exception("Cannot create download file");
            return uri;
        }
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Ashraful Download Manager");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("Cannot create Downloads folder");
        return Uri.fromFile(new File(dir, name));
    }

    public static String mimeFromName(String name) {
        String n = name.toLowerCase(Locale.US);
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".m4a")) return "audio/mp4";
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".zip")) return "application/zip";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".apk")) return "application/vnd.android.package-archive";
        return "application/octet-stream";
    }

    public static String guessName(String url, String contentDisposition) {
        if (contentDisposition != null) {
            String lower = contentDisposition.toLowerCase(Locale.US);
            int p = lower.indexOf("filename=");
            if (p >= 0) {
                String x = contentDisposition.substring(p + 9).trim().replace("\"", "");
                if (!x.isEmpty()) return safeName(x);
            }
        }
        try {
            String path = new URI(url).getPath();
            if (path != null) {
                int slash = path.lastIndexOf('/');
                if (slash >= 0 && slash < path.length() - 1) return safeName(path.substring(slash + 1));
            }
        } catch (Exception ignored) {}
        return "download-" + System.currentTimeMillis();
    }

    public static String safeName(String n) {
        String x = n.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (x.isEmpty()) x = "download.bin";
        return x.length() > 180 ? x.substring(0, 180) : x;
    }
}
