package com.ashraful.fastdownload;

import android.Manifest;
import android.app.Activity;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private LinearLayout listBox;
    private EditText urlInput, nameInput;
    private Switch wifiOnly;
    private static final int REQ_NOTIFY = 77;
    private static final int REQ_STORAGE = 78;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refresher = new Runnable() { @Override public void run() { refresh(); handler.postDelayed(this, 1200); } };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DownloadStore.init(this);
        buildUi();
        importSharedUrl(getIntent());
        handler.postDelayed(refresher, 1200);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFY);
        if (Build.VERSION.SDK_INT <= 28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(247,249,252));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(16),dp(12),dp(16),dp(24)); scroll.addView(root);

        TextView title = tv("ASHRAFUL DOWNLOAD MANAGER", 22, Color.WHITE); title.setTypeface(null, 1); title.setGravity(Gravity.CENTER_VERTICAL); title.setPadding(dp(18),0,dp(12),0);
        GradientLayout header = new GradientLayout(this); header.setPadding(0,0,0,0); header.addView(title, new LinearLayout.LayoutParams(-1, dp(62))); root.addView(header, new LinearLayout.LayoutParams(-1, dp(62)));
        TextView sub = tv("Fast • Resume • Background • ColorOS friendly", 13, Color.rgb(96,112,135)); sub.setPadding(dp(4),dp(8),dp(4),dp(10)); root.addView(sub);

        LinearLayout inputCard = card(); root.addView(inputCard);
        urlInput = edit("Paste direct download URL", "https://"); inputCard.addView(urlInput);
        nameInput = edit("File name (optional)", ""); inputCard.addView(nameInput, lpTop(8));
        wifiOnly = new Switch(this); wifiOnly.setText("Wi‑Fi only"); wifiOnly.setTextColor(Color.rgb(21,32,51)); inputCard.addView(wifiOnly, lpTop(3));
        Button add = button("START DOWNLOAD", true); inputCard.addView(add, lpTop(6)); add.setOnClickListener(v -> addDownload());

        LinearLayout tools = card(); root.addView(tools, lpTop(12));
        TextView toolTitle = tv("ColorOS / battery help", 16, Color.rgb(21,32,51)); toolTitle.setTypeface(null, 1); tools.addView(toolTitle);
        TextView toolText = tv("For long downloads, allow this app to run without battery restrictions in ColorOS settings.", 13, Color.rgb(96,112,135)); tools.addView(toolText, lpTop(3));
        LinearLayout toolRow = new LinearLayout(this); toolRow.setOrientation(LinearLayout.HORIZONTAL);
        Button battery = button("Battery settings", false); Button appInfo = button("App info", false); toolRow.addView(battery, new LinearLayout.LayoutParams(0,dp(46),1)); toolRow.addView(appInfo, lp(0,dp(46),1)); tools.addView(toolRow, lpTop(7));
        battery.setOnClickListener(v -> openBatterySettings()); appInfo.setOnClickListener(v -> openAppInfo());

        TextView history = tv("Downloads", 19, Color.rgb(21,32,51)); history.setTypeface(null,1); history.setPadding(dp(4),dp(15),dp(4),dp(7)); root.addView(history);
        listBox = new LinearLayout(this); listBox.setOrientation(LinearLayout.VERTICAL); root.addView(listBox);
        setContentView(scroll);
        refresh();
    }

    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); importSharedUrl(intent); }

    @Override protected void onDestroy() { handler.removeCallbacks(refresher); super.onDestroy(); }

    private void importSharedUrl(Intent intent) {
        if (intent == null || urlInput == null) return;
        if (Intent.ACTION_SEND.equals(intent.getAction()) && intent.getType() != null && intent.getType().startsWith("text/")) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null && !text.trim().isEmpty()) { urlInput.setText(text.trim()); urlInput.setSelection(urlInput.length()); }
        }
    }

    private void addDownload() {
        String url = urlInput.getText().toString().trim();
        if (!(url.startsWith("http://") || url.startsWith("https://"))) { toast("Please enter a direct http/https download link"); return; }
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) name = DownloadEngine.guessName(url, null); else name = DownloadEngine.safeName(name);
        try {
            DownloadItem item = new DownloadItem(System.currentTimeMillis()); item.url=url; item.name=name; item.wifiOnly=wifiOnly.isChecked(); item.createdAt=System.currentTimeMillis(); item.status=DownloadItem.QUEUED; item.total=-1; item.jobId=(int)(item.id & 0x3fffffff);
            Uri dest = DownloadEngine.createDestination(this, item.name, DownloadEngine.mimeFromName(item.name)); item.uri=dest.toString(); DownloadStore.upsert(this,item);
            schedule(this,item);
            urlInput.setText(""); nameInput.setText(""); toast("Download started"); refresh();
        } catch (Exception e) { toast(e.getMessage()==null?"Could not start download":e.getMessage()); }
    }

    public static void schedule(Context c, DownloadItem item) {
        if (Build.VERSION.SDK_INT >= 34) {
            JobInfo.Builder b = new JobInfo.Builder(item.jobId, new ComponentName(c, DownloadJobService.class))
                    .setUserInitiated(true)
                    .setRequiredNetworkType(item.wifiOnly ? JobInfo.NETWORK_TYPE_UNMETERED : JobInfo.NETWORK_TYPE_ANY)
                    .setEstimatedNetworkBytes(JobInfo.NETWORK_BYTES_UNKNOWN, 0)
                    .setMinimumNetworkChunkBytes(1024 * 1024);
            b.setExtras(extras(item.id));
            JobScheduler js=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE); if (js.schedule(b.build()) <= 0) throw new IllegalStateException("Could not schedule download");
        } else {
            Intent i=new Intent(c,DownloadForegroundService.class).putExtra("download_id",item.id);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        }
        item.status=DownloadItem.DOWNLOADING; DownloadStore.upsert(c,item);
    }

    private static android.os.PersistableBundle extras(long id) { android.os.PersistableBundle p=new android.os.PersistableBundle(); p.putLong("download_id",id); return p; }

    private void pause(DownloadItem item) {
        item.status=DownloadItem.PAUSED;
        if (Build.VERSION.SDK_INT >= 34) { JobScheduler js=getSystemService(JobScheduler.class); if (js != null) js.cancel(item.jobId); }
        else { Intent i=new Intent(this,DownloadForegroundService.class); stopService(i); } item.message="Paused by user"; DownloadStore.upsert(this,item); refresh();
    }
    private void resume(DownloadItem item) { try { item.status=DownloadItem.QUEUED; DownloadStore.upsert(this,item); schedule(this,item); refresh(); } catch(Exception e){toast(e.getMessage());} }
    private void cancel(DownloadItem item) { pause(item); item.status=DownloadItem.CANCELLED; DownloadStore.upsert(this,item); refresh(); }

    private void refresh() {
        if (listBox == null) return;
        listBox.removeAllViews(); List<DownloadItem> items=DownloadStore.all(this);
        if (items.isEmpty()) { TextView empty=tv("No downloads yet. Paste a direct file URL above.",14,Color.rgb(96,112,135)); empty.setGravity(Gravity.CENTER); empty.setPadding(0,dp(28),0,dp(28)); listBox.addView(empty); return; }
        for(DownloadItem item:items) listBox.addView(row(item), lpTop(8));
    }

    private View row(final DownloadItem item) {
        LinearLayout c=card();
        TextView name=tv(item.name,16,Color.rgb(21,32,51)); name.setTypeface(null,1); c.addView(name);
        TextView status=tv(formatStatus(item),13,Color.rgb(96,112,135)); c.addView(status,lpTop(3));
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); bar.setMax(100); int pct=item.total>0?(int)Math.min(100,item.downloaded*100/item.total):0; bar.setProgress(pct); c.addView(bar,lpTop(5));
        LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL);
        if (item.status.equals(DownloadItem.COMPLETED)) { Button open=button("OPEN",false); r.addView(open,new LinearLayout.LayoutParams(0,dp(42),1)); open.setOnClickListener(v -> openFile(item)); }
        else if (item.status.equals(DownloadItem.DOWNLOADING) || item.status.equals(DownloadItem.QUEUED)) { Button p=button("PAUSE",false); Button x=button("CANCEL",false); r.addView(p,new LinearLayout.LayoutParams(0,dp(42),1)); r.addView(x,lp(6,dp(42),1)); p.setOnClickListener(v->pause(item)); x.setOnClickListener(v->cancel(item)); }
        else if (item.status.equals(DownloadItem.PAUSED) || item.status.equals(DownloadItem.FAILED)) { Button rs=button("RESUME",false); Button x=button("REMOVE",false); r.addView(rs,new LinearLayout.LayoutParams(0,dp(42),1)); r.addView(x,lp(6,dp(42),1)); rs.setOnClickListener(v->resume(item)); x.setOnClickListener(v->{DownloadStore.remove(this,item.id);refresh();}); }
        else { Button rs=button("RESUME",false); Button x=button("REMOVE",false); r.addView(rs,new LinearLayout.LayoutParams(0,dp(42),1)); r.addView(x,lp(6,dp(42),1)); rs.setOnClickListener(v->resume(item)); x.setOnClickListener(v->{DownloadStore.remove(this,item.id);refresh();}); }
        c.addView(r,lpTop(6)); return c;
    }

    private String formatStatus(DownloadItem x) { if(x.total>0) return (formatBytes(x.downloaded)+" / "+formatBytes(x.total)+" • "+x.status); return (formatBytes(x.downloaded)+" • "+x.status + (x.message.isEmpty()?"":" • "+x.message)); }
    private String formatBytes(long b) { if(b<1024) return b+" B"; double k=b/1024d; if(k<1024)return String.format(Locale.US,"%.1f KB",k); double m=k/1024d; if(m<1024)return String.format(Locale.US,"%.1f MB",m); return String.format(Locale.US,"%.2f GB",m/1024d); }

    private void openFile(DownloadItem item) { try { Intent i=new Intent(Intent.ACTION_VIEW); Uri u=Uri.parse(item.uri); i.setDataAndType(u,DownloadEngine.mimeFromName(item.name)); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(i); } catch(Exception e){toast("No app can open this file");} }
    private void openBatterySettings() { try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); } catch(Exception e){openAppInfo();} }
    private void openAppInfo() { Intent i=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())); startActivity(i); }
    private void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }
    private TextView tv(String s,int size,int color){ TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); return t; }
    private EditText edit(String hint,String text){ EditText e=new EditText(this); e.setHint(hint); e.setText(text); e.setTextSize(15); e.setSingleLine(true); e.setPadding(dp(12),0,dp(12),0); e.setBackgroundColor(Color.WHITE); return e; }
    private Button button(String text,boolean primary){ Button b=new Button(this); b.setText(text); b.setTextSize(13); b.setAllCaps(false); b.setTextColor(primary?Color.WHITE:Color.rgb(21,32,51)); b.setBackgroundColor(primary?Color.rgb(21,101,192):Color.WHITE); return b; }
    private LinearLayout card(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(12),dp(12),dp(12),dp(12)); l.setBackgroundColor(Color.WHITE); return l; }
    private LinearLayout.LayoutParams lpTop(int top){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,LinearLayout.LayoutParams.WRAP_CONTENT); p.setMargins(0,dp(top),0,0); return p; }
    private LinearLayout.LayoutParams lp(int ml,int h,int weight){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,h,weight); p.setMargins(ml,0,0,0); return p; }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}

    public static class GradientLayout extends LinearLayout { public GradientLayout(Context c){super(c);setBackgroundColor(Color.rgb(13,71,161));setOrientation(VERTICAL);} }
}
