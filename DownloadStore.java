package com.ashraful.fastdownload;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class DownloadStore {
    private static final String PREF = "downloads_v1";
    private static final String KEY = "items";
    private static SharedPreferences prefs;

    private DownloadStore() {}

    public static synchronized void init(Context c) {
        if (prefs == null) prefs = c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static synchronized void upsert(Context c, DownloadItem item) {
        init(c);
        List<DownloadItem> list = all(c);
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == item.id) { list.set(i, item); replaced = true; break; }
        }
        if (!replaced) list.add(item);
        save(list);
    }

    public static synchronized DownloadItem get(Context c, long id) {
        for (DownloadItem x : all(c)) if (x.id == id) return x;
        return null;
    }

    public static synchronized List<DownloadItem> all(Context c) {
        init(c);
        List<DownloadItem> out = new ArrayList<>();
        String raw = prefs.getString(KEY, "[]");
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                DownloadItem x = new DownloadItem(o.getLong("id"));
                x.url = o.optString("url", "");
                x.name = o.optString("name", "download.bin");
                x.uri = o.optString("uri", "");
                x.downloaded = o.optLong("downloaded", 0);
                x.total = o.optLong("total", -1);
                x.status = o.optString("status", DownloadItem.PAUSED);
                x.message = o.optString("message", "");
                x.wifiOnly = o.optBoolean("wifiOnly", false);
                x.createdAt = o.optLong("createdAt", System.currentTimeMillis());
                x.jobId = o.optInt("jobId", 0);
                out.add(x);
            }
        } catch (Exception ignored) {}
        out.sort((a,b) -> Long.compare(b.createdAt, a.createdAt));
        return out;
    }

    public static synchronized void remove(Context c, long id) {
        List<DownloadItem> list = all(c);
        list.removeIf(x -> x.id == id);
        save(list);
    }

    private static void save(List<DownloadItem> list) {
        JSONArray a = new JSONArray();
        try {
            for (DownloadItem x : list) {
                JSONObject o = new JSONObject();
                o.put("id", x.id); o.put("url", x.url); o.put("name", x.name); o.put("uri", x.uri);
                o.put("downloaded", x.downloaded); o.put("total", x.total); o.put("status", x.status);
                o.put("message", x.message); o.put("wifiOnly", x.wifiOnly); o.put("createdAt", x.createdAt); o.put("jobId", x.jobId);
                a.put(o);
            }
        } catch (Exception ignored) {}
        prefs.edit().putString(KEY, a.toString()).apply();
    }
}
