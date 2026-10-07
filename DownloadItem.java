package com.ashraful.fastdownload;

public final class DownloadItem {
    public static final String QUEUED = "QUEUED";
    public static final String DOWNLOADING = "DOWNLOADING";
    public static final String PAUSED = "PAUSED";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";
    public static final String CANCELLED = "CANCELLED";

    public final long id;
    public String url;
    public String name;
    public String uri;
    public long downloaded;
    public long total;
    public String status;
    public String message;
    public boolean wifiOnly;
    public long createdAt;
    public int jobId;

    public DownloadItem(long id) { this.id = id; }
}
