package com.altaf.ioslauncher;

import android.app.Activity;
import android.app.Dialog;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    private final List<AppItem> allApps = new ArrayList<>();
    private final List<AppItem> searchApps = new ArrayList<>();

    private SharedPreferences prefs;
    private FrameLayout root;
    private LinearLayout content;
    private ViewPager2 pager;
    private LinearLayout dots;
    private LinearLayout dock;
    private TextView batteryText;
    private FrameLayout searchOverlay;
    private GridView searchGrid;
    private SearchAdapter searchAdapter;

    private int columns;
    private int rows;
    private int iconDp;
    private boolean showLabels;
    private boolean haptics;
    private int glassAlpha;
    private String theme;

    private float touchDownX;
    private float touchDownY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        prefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE);
        loadPreferences();
        loadApps();
        buildLauncher();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateBattery();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            touchDownX = ev.getX();
            touchDownY = ev.getY();
        } else if (ev.getAction() == MotionEvent.ACTION_UP && searchOverlay != null && searchOverlay.getVisibility() != View.VISIBLE) {
            float dx = ev.getX() - touchDownX;
            float dy = ev.getY() - touchDownY;
            if (dy > dp(105) && Math.abs(dx) < dp(90)) {
                showSearch();
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private void configureWindow() {
        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.BLACK);
        w.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
    }

    private void loadPreferences() {
        theme = prefs.getString("theme", "BLUE");
        columns = prefs.getInt("columns", 4);
        rows = prefs.getInt("rows", 5);
        iconDp = prefs.getInt("icon_dp", 58);
        showLabels = prefs.getBoolean("labels", true);
        haptics = prefs.getBoolean("haptics", true);
        glassAlpha = prefs.getInt("glass_alpha", 82);
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN, null);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(query, 0);

        allApps.clear();
        for (ResolveInfo r : list) {
            if (r.activityInfo.packageName.equals(getPackageName())) continue;
            String label = r.loadLabel(pm).toString();
            ComponentName c = new ComponentName(r.activityInfo.packageName, r.activityInfo.name);
            allApps.add(new AppItem(label, c, r.loadIcon(pm)));
        }
        Collections.sort(allApps, Comparator.comparing(a -> a.label.toLowerCase(Locale.ROOT)));
        searchApps.clear();
        searchApps.addAll(allApps);
    }

    private void buildLauncher() {
        root = new FrameLayout(this);
        applyWallpaper();

        addGlow(root, -70, 90, 300, Color.argb(theme.equals("AMOLED") ? 12 : 36, 90, 160, 255));
        addGlow(root, 210, 420, 260, Color.argb(theme.equals("AMOLED") ? 8 : 28, 210, 80, 230));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(34), dp(16), dp(14));
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        buildStatusBar();
        buildTopWidget();
        buildPager();
        buildSearchPill();
        buildDock();
        buildSearchOverlay();

        root.setOnLongClickListener(v -> {
            showSettings();
            return true;
        });

        setContentView(root);
        root.setAlpha(0f);
        root.setScaleX(.985f);
        root.setScaleY(.985f);
        root.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(330).setInterpolator(new DecelerateInterpolator()).start();
    }

    private void buildStatusBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4), 0, dp(4), 0);

        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("HH:mm");
        time.setTextColor(Color.WHITE);
        time.setTextSize(14);
        time.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        bar.addView(time, new LinearLayout.LayoutParams(0, dp(28), 1f));

        batteryText = new TextView(this);
        batteryText.setTextColor(Color.WHITE);
        batteryText.setTextSize(12);
        batteryText.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        batteryText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        bar.addView(batteryText, new LinearLayout.LayoutParams(0, dp(28), 1f));
        updateBattery();

        content.addView(bar, new LinearLayout.LayoutParams(-1, dp(30)));
    }

    private void buildTopWidget() {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(18), dp(10), dp(10), dp(10));
        card.setBackground(glassRound(26));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER_VERTICAL);

        TextClock bigTime = new TextClock(this);
        bigTime.setFormat12Hour("h:mm");
        bigTime.setFormat24Hour("HH:mm");
        bigTime.setTextColor(Color.WHITE);
        bigTime.setTextSize(31);
        bigTime.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL), Typeface.BOLD);
        left.addView(bigTime, new LinearLayout.LayoutParams(-1, dp(43)));

        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTextColor(Color.argb(220,255,255,255));
        date.setTextSize(13);
        left.addView(date, new LinearLayout.LayoutParams(-1, dp(24)));

        card.addView(left, new LinearLayout.LayoutParams(0, -1, 1f));

        TextView gear = new TextView(this);
        gear.setText("⚙");
        gear.setTextColor(Color.WHITE);
        gear.setTextSize(25);
        gear.setGravity(Gravity.CENTER);
        gear.setBackground(round(Color.argb(Math.min(120, glassAlpha + 20),255,255,255), 22));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(dp(48), dp(48));
        card.addView(gear, gp);
        gear.setOnClickListener(v -> {
            press(v);
            showSettings();
        });

        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, dp(92));
        cp.setMargins(0, dp(7), 0, dp(8));
        content.addView(card, cp);
    }

    private void buildPager() {
        FrameLayout area = new FrameLayout(this);

        pager = new ViewPager2(this);
        pager.setOffscreenPageLimit(1);
        pager.setAdapter(new PageAdapter());
        pager.setPageTransformer((page, position) -> {
            float p = Math.abs(position);
            page.setAlpha(1f - Math.min(.22f, p * .22f));
            page.setScaleX(1f - Math.min(.025f, p * .025f));
            page.setScaleY(1f - Math.min(.025f, p * .025f));
            page.setTranslationX(-position * dp(9));
        });
        area.addView(pager, new FrameLayout.LayoutParams(-1, -1));

        content.addView(area, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    private void buildSearchPill() {
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.setGravity(Gravity.CENTER);

        dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER);
        holder.addView(dots, new LinearLayout.LayoutParams(-1, dp(18)));
        rebuildDots(0);

        TextView search = new TextView(this);
        search.setText("⌕  Search");
        search.setTextColor(Color.WHITE);
        search.setTextSize(13);
        search.setGravity(Gravity.CENTER);
        search.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        search.setBackground(round(Color.argb(Math.max(45, glassAlpha - 20), 25, 30, 45), 18));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(124), dp(34));
        holder.addView(search, sp);
        search.setOnClickListener(v -> {
            press(v);
            showSearch();
        });

        content.addView(holder, new LinearLayout.LayoutParams(-1, dp(58)));

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                rebuildDots(position);
            }
        });
    }

    private void buildDock() {
        dock = new LinearLayout(this);
        dock.setGravity(Gravity.CENTER);
        dock.setPadding(dp(10), dp(7), dp(10), dp(7));
        dock.setBackground(glassRound(29));

        for (AppItem app : chooseDockApps()) {
            LinearLayout slot = new LinearLayout(this);
            slot.setGravity(Gravity.CENTER);

            ImageView icon = new ImageView(this);
            icon.setImageDrawable(app.icon);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int size = Math.min(62, iconDp + 2);
            slot.addView(icon, new LinearLayout.LayoutParams(dp(size), dp(size)));
            slot.setOnClickListener(v -> openApp(app, icon));

            dock.addView(slot, new LinearLayout.LayoutParams(0, -1, 1f));
        }

        LinearLayout.LayoutParams dpDock = new LinearLayout.LayoutParams(-1, dp(82));
        dpDock.setMargins(0, dp(5), 0, 0);
        content.addView(dock, dpDock);
    }

    private void buildSearchOverlay() {
        searchOverlay = new FrameLayout(this);
        searchOverlay.setVisibility(View.GONE);
        searchOverlay.setBackgroundColor(Color.argb(236, 12, 16, 24));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(52), dp(16), dp(14));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);

        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setHint("Search Apps");
        field.setHintTextColor(Color.argb(165,255,255,255));
        field.setTextColor(Color.WHITE);
        field.setTextSize(17);
        field.setPadding(dp(16),0,dp(16),0);
        field.setBackground(round(Color.argb(82,255,255,255), 22));
        top.addView(field, new LinearLayout.LayoutParams(0, dp(48), 1f));

        TextView cancel = new TextView(this);
        cancel.setText("Cancel");
        cancel.setTextColor(Color.rgb(100,175,255));
        cancel.setTextSize(15);
        cancel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ca = new LinearLayout.LayoutParams(dp(76), dp(48));
        ca.setMargins(dp(6),0,0,0);
        top.addView(cancel, ca);
        panel.addView(top, new LinearLayout.LayoutParams(-1, dp(52)));

        searchGrid = new GridView(this);
        searchGrid.setNumColumns(4);
        searchGrid.setVerticalSpacing(dp(14));
        searchGrid.setHorizontalSpacing(dp(5));
        searchGrid.setPadding(0,dp(16),0,dp(10));
        searchGrid.setClipToPadding(false);
        searchGrid.setSelector(android.R.color.transparent);
        searchAdapter = new SearchAdapter();
        searchGrid.setAdapter(searchAdapter);
        panel.addView(searchGrid, new LinearLayout.LayoutParams(-1,0,1f));

        searchOverlay.addView(panel, new FrameLayout.LayoutParams(-1,-1));
        root.addView(searchOverlay, new FrameLayout.LayoutParams(-1,-1));

        field.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a) {}
            public void onTextChanged(CharSequence s,int st,int b,int c) { filterSearch(s.toString()); }
            public void afterTextChanged(android.text.Editable e) {}
        });
        searchGrid.setOnItemClickListener((p,v,pos,id) -> openApp(searchApps.get(pos), v));
        cancel.setOnClickListener(v -> hideSearch());
        searchOverlay.setOnClickListener(v -> {
            if (v == searchOverlay) hideSearch();
        });
        searchOverlay.setTag(field);
    }

    private void showSearch() {
        if (searchOverlay.getVisibility() == View.VISIBLE) return;
        searchApps.clear();
        searchApps.addAll(allApps);
        searchAdapter.notifyDataSetChanged();
        EditText field = (EditText) searchOverlay.getTag();
        field.setText("");
        searchOverlay.setVisibility(View.VISIBLE);
        searchOverlay.setAlpha(0f);
        searchOverlay.setTranslationY(dp(22));
        searchOverlay.animate().alpha(1f).translationY(0).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
        field.requestFocus();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    private void hideSearch() {
        searchOverlay.animate().alpha(0f).translationY(dp(18)).setDuration(170).withEndAction(() -> {
            searchOverlay.setVisibility(View.GONE);
            searchOverlay.setTranslationY(0);
        }).start();
    }

    private void filterSearch(String text) {
        String q = text.trim().toLowerCase(Locale.ROOT);
        searchApps.clear();
        for (AppItem a : allApps) {
            if (q.isEmpty() || a.label.toLowerCase(Locale.ROOT).contains(q)) searchApps.add(a);
        }
        searchAdapter.notifyDataSetChanged();
    }

    private void showSettings() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(20), dp(20), dp(24));
        panel.setBackground(round(Color.rgb(30,33,42), 28));
        scroll.addView(panel, new ScrollView.LayoutParams(-1,-2));

        TextView title = title("Launcher Settings");
        panel.addView(title, new LinearLayout.LayoutParams(-1, dp(44)));

        TextView sub = text("Customize your iOS-style Home Screen", 13, Color.argb(180,255,255,255));
        panel.addView(sub, new LinearLayout.LayoutParams(-1, dp(32)));

        panel.addView(section("Theme"));
        LinearLayout themes = chipRow();
        addChip(themes, "iOS Blue", "BLUE", dialog);
        addChip(themes, "Purple", "PURPLE", dialog);
        addChip(themes, "AMOLED", "AMOLED", dialog);
        addChip(themes, "Graphite", "GRAPHITE", dialog);
        panel.addView(themes, new LinearLayout.LayoutParams(-1, dp(48)));

        panel.addView(section("Home Screen Grid"));
        LinearLayout grids = chipRow();
        addGridChip(grids, "4 × 5", 4, 5, dialog);
        addGridChip(grids, "5 × 6", 5, 6, dialog);
        panel.addView(grids, new LinearLayout.LayoutParams(-1, dp(48)));

        panel.addView(section("Icon Size"));
        TextView iconValue = text(iconDp + " dp", 13, Color.WHITE);
        panel.addView(iconValue, new LinearLayout.LayoutParams(-1, dp(28)));
        SeekBar iconSeek = new SeekBar(this);
        iconSeek.setMax(24);
        iconSeek.setProgress(Math.max(0, Math.min(24, iconDp - 46)));
        panel.addView(iconSeek, new LinearLayout.LayoutParams(-1, dp(48)));
        iconSeek.setOnSeekBarChangeListener(new SimpleSeek() {
            public void onProgressChanged(SeekBar s,int progress,boolean fromUser) {
                iconDp = 46 + progress;
                iconValue.setText(iconDp + " dp");
                prefs.edit().putInt("icon_dp", iconDp).apply();
            }
            public void onStopTrackingTouch(SeekBar s) { rebuildAfterSettings(dialog); }
        });

        panel.addView(section("Glass Strength"));
        TextView glassValue = text(glassAlpha + "%", 13, Color.WHITE);
        panel.addView(glassValue, new LinearLayout.LayoutParams(-1, dp(28)));
        SeekBar glassSeek = new SeekBar(this);
        glassSeek.setMax(100);
        glassSeek.setProgress(glassAlpha);
        panel.addView(glassSeek, new LinearLayout.LayoutParams(-1, dp(48)));
        glassSeek.setOnSeekBarChangeListener(new SimpleSeek() {
            public void onProgressChanged(SeekBar s,int progress,boolean fromUser) {
                glassAlpha = Math.max(25, progress);
                glassValue.setText(glassAlpha + "%");
                prefs.edit().putInt("glass_alpha", glassAlpha).apply();
            }
            public void onStopTrackingTouch(SeekBar s) { rebuildAfterSettings(dialog); }
        });

        Switch labels = new Switch(this);
        labels.setText("Show App Labels");
        labels.setTextColor(Color.WHITE);
        labels.setTextSize(15);
        labels.setChecked(showLabels);
        labels.setPadding(0,dp(8),0,dp(8));
        panel.addView(labels, new LinearLayout.LayoutParams(-1, dp(52)));
        labels.setOnCheckedChangeListener((b,checked) -> {
            showLabels = checked;
            prefs.edit().putBoolean("labels", checked).apply();
            rebuildAfterSettings(dialog);
        });

        Switch hapticSwitch = new Switch(this);
        hapticSwitch.setText("Haptic Touch");
        hapticSwitch.setTextColor(Color.WHITE);
        hapticSwitch.setTextSize(15);
        hapticSwitch.setChecked(haptics);
        panel.addView(hapticSwitch, new LinearLayout.LayoutParams(-1, dp(52)));
        hapticSwitch.setOnCheckedChangeListener((b,checked) -> {
            haptics = checked;
            prefs.edit().putBoolean("haptics", checked).apply();
        });

        Button defaultBtn = actionButton("Set as Default Launcher");
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(52));
        bp.setMargins(0,dp(14),0,0);
        panel.addView(defaultBtn, bp);
        defaultBtn.setOnClickListener(v -> {
            dialog.dismiss();
            try {
                startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
            } catch (Exception e) {
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.addCategory(Intent.CATEGORY_HOME);
                startActivity(Intent.createChooser(i, "Choose Home app"));
            }
        });

        Button androidSettings = actionButton("Open Android Settings");
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, dp(48));
        ap.setMargins(0,dp(8),0,0);
        panel.addView(androidSettings, ap);
        androidSettings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_SETTINGS)));

        TextView hint = text("Tip: Home Screen par long-press se ye panel kabhi bhi khol sakte ho. Swipe down se Spotlight search khulta hai.", 12, Color.argb(170,255,255,255));
        hint.setPadding(0,dp(16),0,0);
        panel.addView(hint, new LinearLayout.LayoutParams(-1, dp(70)));

        dialog.setContentView(scroll);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            w.setDimAmount(.45f);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
        }
        dialog.setOnShowListener(d -> {
            Window ww = dialog.getWindow();
            if (ww != null) ww.setLayout(WindowManager.LayoutParams.MATCH_PARENT, (int)(getResources().getDisplayMetrics().heightPixels * .82f));
        });
        dialog.show();
    }

    private void addChip(LinearLayout row, String label, String value, Dialog dialog) {
        TextView chip = chip(label, theme.equals(value));
        row.addView(chip, new LinearLayout.LayoutParams(0, dp(40), 1f));
        chip.setOnClickListener(v -> {
            theme = value;
            prefs.edit().putString("theme", value).apply();
            rebuildAfterSettings(dialog);
        });
    }

    private void addGridChip(LinearLayout row, String label, int c, int r, Dialog dialog) {
        TextView chip = chip(label, columns == c && rows == r);
        row.addView(chip, new LinearLayout.LayoutParams(0, dp(40), 1f));
        chip.setOnClickListener(v -> {
            columns = c;
            rows = r;
            prefs.edit().putInt("columns", c).putInt("rows", r).apply();
            rebuildAfterSettings(dialog);
        });
    }

    private void rebuildAfterSettings(Dialog dialog) {
        dialog.dismiss();
        buildLauncher();
    }

    private LinearLayout chipRow() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private TextView chip(String label, boolean selected) {
        TextView t = text(label, 12, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        t.setBackground(round(selected ? Color.rgb(55,120,245) : Color.rgb(57,60,72), 17));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
        lp.setMargins(dp(3),0,dp(3),0);
        t.setLayoutParams(lp);
        return t;
    }

    private TextView section(String label) {
        TextView t = text(label, 13, Color.rgb(130,185,255));
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(0,dp(13),0,dp(5));
        t.setAllCaps(false);
        return t;
    }

    private Button actionButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(round(Color.rgb(45,110,235), 18));
        return b;
    }

    private List<AppItem> chooseDockApps() {
        List<AppItem> out = new ArrayList<>();
        Set<ComponentName> used = new HashSet<>();
        String[][] groups = {
                {"phone","dialer","contacts"},
                {"message","messages","sms"},
                {"chrome","browser"},
                {"camera"}
        };
        for (String[] group : groups) {
            AppItem match = findByKeywords(group, used);
            if (match != null) {
                out.add(match);
                used.add(match.component);
            }
        }
        for (AppItem a : allApps) {
            if (out.size() >= 4) break;
            if (!used.contains(a.component)) {
                out.add(a);
                used.add(a.component);
            }
        }
        return out;
    }

    private AppItem findByKeywords(String[] words, Set<ComponentName> used) {
        for (String w : words) {
            for (AppItem a : allApps) {
                if (!used.contains(a.component) && a.label.toLowerCase(Locale.ROOT).contains(w)) return a;
            }
        }
        return null;
    }

    private void updateBattery() {
        if (batteryText == null) return;
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        int pct = bm == null ? -1 : bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        batteryText.setText("▮▮▮   Wi-Fi   " + (pct >= 0 ? pct + "%" : ""));
    }

    private void openApp(AppItem app, View pressed) {
        if (haptics) pressed.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        pressed.animate().scaleX(.84f).scaleY(.84f).setDuration(75).withEndAction(() -> {
            pressed.animate().scaleX(1f).scaleY(1f).setDuration(165).setInterpolator(new DecelerateInterpolator()).start();
            try {
                Intent i = new Intent();
                i.setComponent(app.component);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Exception e) {
                Toast.makeText(this, "App open nahi hua", Toast.LENGTH_SHORT).show();
            }
        }).start();
    }

    private void press(View v) {
        if (haptics) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        v.animate().scaleX(.92f).scaleY(.92f).setDuration(70).withEndAction(() ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        ).start();
    }

    private void rebuildDots(int selected) {
        if (dots == null || pager == null || pager.getAdapter() == null) return;
        dots.removeAllViews();
        int count = pager.getAdapter().getItemCount();
        if (count > 8) {
            TextView page = text((selected + 1) + " / " + count, 11, Color.argb(210,255,255,255));
            page.setGravity(Gravity.CENTER);
            dots.addView(page, new LinearLayout.LayoutParams(dp(50), dp(18)));
            return;
        }
        for (int i=0;i<count;i++) {
            TextView d = new TextView(this);
            d.setText(i == selected ? "●" : "•");
            d.setTextColor(i == selected ? Color.WHITE : Color.argb(135,255,255,255));
            d.setTextSize(i == selected ? 9 : 14);
            d.setGravity(Gravity.CENTER);
            dots.addView(d, new LinearLayout.LayoutParams(dp(14), dp(18)));
        }
    }

    private void applyWallpaper() {
        if ("AMOLED".equals(theme)) {
            root.setBackground(wallpaper(new int[]{0xFF000000,0xFF070707,0xFF000000}));
        } else if ("PURPLE".equals(theme)) {
            root.setBackground(wallpaper(new int[]{0xFF140B2D,0xFF54269B,0xFFB2348E}));
        } else if ("GRAPHITE".equals(theme)) {
            root.setBackground(wallpaper(new int[]{0xFF0B0F16,0xFF263346,0xFF111827}));
        } else if ("SYSTEM".equals(theme)) {
            try {
                Drawable d = WallpaperManager.getInstance(this).getDrawable();
                root.setBackground(d);
            } catch (Exception e) {
                root.setBackground(wallpaper(new int[]{0xFF10285D,0xFF2869D8,0xFF1438A0}));
            }
        } else {
            root.setBackground(wallpaper(new int[]{0xFF0B1D46,0xFF2458C8,0xFF1C8CFF}));
        }
    }

    private Drawable wallpaper(int[] colors) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        g.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        return g;
    }

    private Drawable glassRound(int radius) {
        boolean dark = "AMOLED".equals(theme) || "GRAPHITE".equals(theme);
        int alpha = Math.max(35, Math.min(155, glassAlpha + 20));
        int base = dark ? Color.argb(alpha, 35, 39, 48) : Color.argb(alpha, 235, 242, 255);
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                dark
                        ? new int[]{base, Color.argb(Math.max(25, alpha-25), 20,24,32)}
                        : new int[]{base, Color.argb(Math.max(25, alpha-35), 145,180,235)}
        );
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(1), Color.argb(46,255,255,255));
        return g;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private void addGlow(FrameLayout parent, int x, int y, int size, int color) {
        View glow = new View(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(color);
        glow.setBackground(bg);
        glow.setAlpha(.65f);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(size), dp(size));
        lp.leftMargin = dp(x);
        lp.topMargin = dp(y);
        parent.addView(glow, lp);
    }

    private TextView title(String value) {
        TextView t = text(value, 23, Color.WHITE);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private TextView text(String value, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private final class PageAdapter extends RecyclerView.Adapter<PageHolder> {
        private final int pageSize = columns * rows;

        @Override public PageHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            FrameLayout f = new FrameLayout(MainActivity.this);
            f.setLayoutParams(new ViewGroup.LayoutParams(-1,-1));
            return new PageHolder(f);
        }

        @Override public void onBindViewHolder(PageHolder holder, int position) {
            holder.frame.removeAllViews();
            GridLayout grid = new GridLayout(MainActivity.this);
            grid.setColumnCount(columns);
            grid.setRowCount(rows);
            grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
            grid.setUseDefaultMargins(false);
            grid.setPadding(0, dp(3), 0, dp(3));

            int start = position * pageSize;
            for (int cell=0; cell<pageSize; cell++) {
                int index = start + cell;
                View child;
                if (index < allApps.size()) child = appCell(allApps.get(index));
                else child = new View(MainActivity.this);

                GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                        GridLayout.spec(cell / columns, 1, 1f),
                        GridLayout.spec(cell % columns, 1, 1f)
                );
                lp.width = 0;
                lp.height = 0;
                grid.addView(child, lp);
            }
            holder.frame.addView(grid, new FrameLayout.LayoutParams(-1,-1));
        }

        @Override public int getItemCount() {
            return Math.max(1, (int)Math.ceil(allApps.size() / (double)pageSize));
        }
    }

    private View appCell(AppItem app) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(2), dp(2), dp(2), dp(2));

        FrameLayout iconFrame = new FrameLayout(this);
        iconFrame.setBackground(round(Color.argb(theme.equals("AMOLED") ? 18 : 24,255,255,255), 18));

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int realSize = Math.max(44, Math.min(iconDp, columns == 5 ? 54 : 66));
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(dp(realSize), dp(realSize), Gravity.CENTER);
        iconFrame.addView(icon, ip);

        int frameSize = realSize + 6;
        box.addView(iconFrame, new LinearLayout.LayoutParams(dp(frameSize), dp(frameSize)));

        TextView label = new TextView(this);
        label.setText(app.label);
        label.setTextColor(Color.WHITE);
        label.setTextSize(columns == 5 ? 10 : 11);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setShadowLayer(4,0,1,Color.argb(190,0,0,0));
        label.setVisibility(showLabels ? View.VISIBLE : View.INVISIBLE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(23));
        lp.setMargins(dp(2),dp(3),dp(2),0);
        box.addView(label, lp);

        box.setOnClickListener(v -> openApp(app, iconFrame));
        box.setOnLongClickListener(v -> {
            showSettings();
            return true;
        });
        return box;
    }

    private final class SearchAdapter extends BaseAdapter {
        public int getCount() { return searchApps.size(); }
        public Object getItem(int p) { return searchApps.get(p); }
        public long getItemId(int p) { return p; }

        public View getView(int p, View old, ViewGroup parent) {
            AppItem app = searchApps.get(p);
            LinearLayout box = new LinearLayout(MainActivity.this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER);

            ImageView icon = new ImageView(MainActivity.this);
            icon.setImageDrawable(app.icon);
            int s = columns == 5 ? 48 : 56;
            box.addView(icon, new LinearLayout.LayoutParams(dp(s),dp(s)));

            TextView label = text(app.label, 10, Color.WHITE);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            box.addView(label, new LinearLayout.LayoutParams(-1,dp(25)));
            return box;
        }
    }

    private static final class PageHolder extends RecyclerView.ViewHolder {
        final FrameLayout frame;
        PageHolder(FrameLayout itemView) {
            super(itemView);
            frame = itemView;
        }
    }

    private static final class AppItem {
        final String label;
        final ComponentName component;
        final Drawable icon;
        AppItem(String label, ComponentName component, Drawable icon) {
            this.label = label;
            this.component = component;
            this.icon = icon;
        }
    }

    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        public void onStartTrackingTouch(SeekBar seekBar) {}
    }
}
