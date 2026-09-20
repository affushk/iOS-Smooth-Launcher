package com.altaf.ioslauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private final List<AppItem> allApps = new ArrayList<>();
    private final List<AppItem> shownApps = new ArrayList<>();
    private AppAdapter adapter;
    private LinearLayout dock;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(18), dp(14), dp(14));
        root.setBackground(wallpaper());

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(16);
        clock.setGravity(Gravity.START);
        clock.setPadding(dp(8), 0, 0, dp(8));
        root.addView(clock, new LinearLayout.LayoutParams(-1, dp(36)));

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search");
        search.setHintTextColor(Color.argb(180,255,255,255));
        search.setTextColor(Color.WHITE);
        search.setTextSize(17);
        search.setPadding(dp(18),0,dp(18),0);
        search.setBackground(round(Color.argb(78,255,255,255), 24));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(48));
        sp.setMargins(0,0,0,dp(10));
        root.addView(search, sp);

        GridView grid = new GridView(this);
        grid.setNumColumns(4);
        grid.setVerticalSpacing(dp(14));
        grid.setHorizontalSpacing(dp(4));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setGravity(Gravity.CENTER);
        grid.setPadding(0, dp(6), 0, dp(8));
        grid.setClipToPadding(false);
        grid.setSelector(android.R.color.transparent);
        root.addView(grid, new LinearLayout.LayoutParams(-1,0,1f));

        dock = new LinearLayout(this);
        dock.setGravity(Gravity.CENTER);
        dock.setPadding(dp(10), dp(8), dp(10), dp(8));
        dock.setBackground(round(Color.argb(92,255,255,255), 28));
        LinearLayout.LayoutParams dpDock = new LinearLayout.LayoutParams(-1, dp(82));
        dpDock.setMargins(0, dp(8), 0, 0);
        root.addView(dock, dpDock);

        setContentView(root);
        loadApps();

        adapter = new AppAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p,v,pos,id) -> openApp(shownApps.get(pos), v));
        grid.setOnItemLongClickListener((p,v,pos,id) -> {
            showLauncherSettings();
            return true;
        });

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a) {}
            public void onTextChanged(CharSequence s,int st,int b,int c) { filter(s.toString()); }
            public void afterTextChanged(Editable e) {}
        });

        root.setAlpha(0f);
        root.setScaleX(.985f);
        root.setScaleY(.985f);
        root.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(360).setInterpolator(new DecelerateInterpolator()).start();
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN, null);
        q.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(q, 0);
        allApps.clear();

        for (ResolveInfo r : list) {
            if (r.activityInfo.packageName.equals(getPackageName())) continue;
            String label = r.loadLabel(pm).toString();
            ComponentName c = new ComponentName(r.activityInfo.packageName, r.activityInfo.name);
            allApps.add(new AppItem(label, c, r.loadIcon(pm)));
        }

        Collections.sort(allApps, Comparator.comparing(a -> a.label.toLowerCase(Locale.ROOT)));
        shownApps.clear();
        shownApps.addAll(allApps);
        buildDock();
    }

    private void filter(String text) {
        String q = text.trim().toLowerCase(Locale.ROOT);
        shownApps.clear();
        for (AppItem a : allApps) {
            if (q.isEmpty() || a.label.toLowerCase(Locale.ROOT).contains(q)) shownApps.add(a);
        }
        adapter.notifyDataSetChanged();
    }

    private void buildDock() {
        dock.removeAllViews();
        int count = Math.min(4, allApps.size());
        for (int i=0;i<count;i++) {
            AppItem a = allApps.get(i);
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(a.icon);
            icon.setPadding(dp(8),dp(8),dp(8),dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            dock.addView(icon, lp);
            icon.setOnClickListener(v -> openApp(a, v));
        }
    }

    private void openApp(AppItem a, View v) {
        v.animate().scaleX(.86f).scaleY(.86f).setDuration(90).withEndAction(() -> {
            v.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
            try {
                Intent i = new Intent();
                i.setComponent(a.component);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Exception e) {
                Toast.makeText(this, "App open nahi hua", Toast.LENGTH_SHORT).show();
            }
        }).start();
    }

    private void showLauncherSettings() {
        new AlertDialog.Builder(this)
                .setTitle("iOS Smooth Launcher")
                .setMessage("Is launcher ko default Home app banana hai?")
                .setPositiveButton("Set Default", (d,w) -> {
                    try { startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }
                    catch (Exception e) {
                        Intent i = new Intent(Intent.ACTION_MAIN);
                        i.addCategory(Intent.CATEGORY_HOME);
                        startActivity(Intent.createChooser(i, "Choose Home app"));
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private GradientDrawable wallpaper() {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(20,75,190), Color.rgb(80,150,245), Color.rgb(40,30,125)});
        g.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        return g;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(1), Color.argb(45,255,255,255));
        return g;
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private final class AppAdapter extends BaseAdapter {
        public int getCount() { return shownApps.size(); }
        public Object getItem(int p) { return shownApps.get(p); }
        public long getItemId(int p) { return p; }

        public View getView(int p, View old, ViewGroup parent) {
            LinearLayout box;
            ImageView icon;
            TextView label;

            if (old == null) {
                box = new LinearLayout(MainActivity.this);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setGravity(Gravity.CENTER);
                box.setPadding(dp(2),dp(6),dp(2),dp(2));

                icon = new ImageView(MainActivity.this);
                icon.setId(1001);
                box.addView(icon, new LinearLayout.LayoutParams(dp(58),dp(58)));

                label = new TextView(MainActivity.this);
                label.setId(1002);
                label.setTextColor(Color.WHITE);
                label.setTextSize(11);
                label.setGravity(Gravity.CENTER);
                label.setSingleLine(true);
                label.setShadowLayer(3,0,1,Color.argb(170,0,0,0));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,dp(24));
                lp.setMargins(0,dp(3),0,0);
                box.addView(label, lp);
            } else {
                box = (LinearLayout) old;
                icon = box.findViewById(1001);
                label = box.findViewById(1002);
            }

            AppItem a = shownApps.get(p);
            icon.setImageDrawable(a.icon);
            label.setText(a.label);
            return box;
        }
    }

    private static final class AppItem {
        final String label;
        final ComponentName component;
        final android.graphics.drawable.Drawable icon;

        AppItem(String label, ComponentName component, android.graphics.drawable.Drawable icon) {
            this.label = label;
            this.component = component;
            this.icon = icon;
        }
    }
}
