package com.altaf.ioslauncher;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
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
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
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

    private final List<AppItem> installedApps = new ArrayList<>();
    private final List<AppItem> visibleApps = new ArrayList<>();
    private final List<AppItem> searchApps = new ArrayList<>();

    private SharedPreferences prefs;
    private FrameLayout root;
    private LinearLayout content;
    private ViewPager2 pager;
    private LinearLayout dots;
    private LinearLayout dock;
    private TextView batteryText;
    private TextView networkText;
    private FrameLayout searchOverlay;
    private GridView searchGrid;
    private SearchAdapter searchAdapter;
    private LinearLayout editBar;

    private final Set<String> hiddenSet = new HashSet<>();
    private boolean editMode = false;

    private int columns;
    private int rows;
    private int iconDp;
    private int labelSp;
    private int glassAlpha;
    private boolean showLabels;
    private boolean haptics;
    private String theme;

    private float touchDownX;
    private float touchDownY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE);
        configureWindow();
        loadPreferences();
        loadApps();
        buildLauncher();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    @Override
    public void onBackPressed() {
        if (searchOverlay != null && searchOverlay.getVisibility() == View.VISIBLE) {
            hideSearch();
            return;
        }
        if (editMode) {
            exitEditMode();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            touchDownX = ev.getX();
            touchDownY = ev.getY();
        } else if (ev.getAction() == MotionEvent.ACTION_UP
                && !editMode
                && searchOverlay != null
                && searchOverlay.getVisibility() != View.VISIBLE) {
            float dx = ev.getX() - touchDownX;
            float dy = ev.getY() - touchDownY;
            if (dy > dp(95) && Math.abs(dx) < dp(90)) showSearch();
        }
        return super.dispatchTouchEvent(ev);
    }

    private void configureWindow() {
        Window w = getWindow();
        w.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        w.setNavigationBarColor(Color.BLACK);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void loadPreferences() {
        theme = prefs.getString("theme", "AMOLED");
        columns = prefs.getInt("columns", 4);
        rows = prefs.getInt("rows", 5);
        iconDp = prefs.getInt("icon_dp", 58);
        labelSp = prefs.getInt("label_sp", 11);
        showLabels = prefs.getBoolean("labels", true);
        haptics = prefs.getBoolean("haptics", true);
        glassAlpha = prefs.getInt("glass_alpha", 70);

        hiddenSet.clear();
        hiddenSet.addAll(prefs.getStringSet("hidden_apps", new HashSet<>()));
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN, null);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(query, 0);

        installedApps.clear();
        for (ResolveInfo r : list) {
            if (r.activityInfo.packageName.equals(getPackageName())) continue;
            String label = r.loadLabel(pm).toString();
            ComponentName c = new ComponentName(r.activityInfo.packageName, r.activityInfo.name);
            installedApps.add(new AppItem(label, c, r.loadIcon(pm)));
        }
        Collections.sort(installedApps, Comparator.comparing(a -> a.label.toLowerCase(Locale.ROOT)));
        refreshVisibleApps();
    }

    private void refreshVisibleApps() {
        visibleApps.clear();
        for (AppItem a : installedApps) {
            if (!hiddenSet.contains(key(a))) visibleApps.add(a);
        }
        searchApps.clear();
        searchApps.addAll(visibleApps);
    }

    private void buildLauncher() {
        root = new FrameLayout(this);
        applyWallpaper();

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(10), dp(16), dp(12));
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        buildStatusBar();
        buildDateCard();
        buildPager();
        buildSearchPill();
        buildDock();
        buildSearchOverlay();

        root.setOnLongClickListener(v -> {
            if (!editMode) showSettings();
            return true;
        });

        setContentView(root);
        if (editMode) attachEditBar();

        root.setAlpha(0f);
        root.setScaleX(.99f);
        root.setScaleY(.99f);
        root.animate()
                .alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(280)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void buildStatusBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(5), 0, dp(5), 0);

        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("HH:mm");
        time.setTextColor(Color.WHITE);
        time.setTextSize(14);
        time.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        bar.addView(time, new LinearLayout.LayoutParams(0, dp(28), 1f));

        LinearLayout right = new LinearLayout(this);
        right.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        networkText = text("", 11, Color.WHITE);
        networkText.setGravity(Gravity.CENTER);
        right.addView(networkText, new LinearLayout.LayoutParams(dp(64), dp(28)));

        batteryText = text("", 12, Color.WHITE);
        batteryText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        batteryText.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        right.addView(batteryText, new LinearLayout.LayoutParams(dp(54), dp(28)));

        bar.addView(right, new LinearLayout.LayoutParams(0, dp(28), 1f));
        content.addView(bar, new LinearLayout.LayoutParams(-1, dp(30)));
        updateStatus();
    }

    private void buildDateCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(17), dp(9), dp(17), dp(8));
        card.setBackground(glassRound(24));

        TextClock day = new TextClock(this);
        day.setFormat12Hour("EEEE");
        day.setFormat24Hour("EEEE");
        day.setTextColor(Color.WHITE);
        day.setTextSize(20);
        day.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(day, new LinearLayout.LayoutParams(-1, dp(31)));

        TextClock date = new TextClock(this);
        date.setFormat12Hour("d MMMM");
        date.setFormat24Hour("d MMMM");
        date.setTextColor(Color.argb(205,255,255,255));
        date.setTextSize(13);
        card.addView(date, new LinearLayout.LayoutParams(-1, dp(22)));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(72));
        lp.setMargins(0, dp(5), 0, dp(7));
        content.addView(card, lp);
    }

    private void buildPager() {
        pager = new ViewPager2(this);
        pager.setOffscreenPageLimit(1);
        pager.setAdapter(new PageAdapter());
        pager.setPageTransformer((page, position) -> {
            float p = Math.abs(position);
            page.setAlpha(1f - Math.min(.16f, p * .16f));
            page.setScaleX(1f - Math.min(.018f, p * .018f));
            page.setScaleY(1f - Math.min(.018f, p * .018f));
            page.setTranslationX(-position * dp(7));
        });

        RecyclerView inner = (RecyclerView) pager.getChildAt(0);
        inner.setOverScrollMode(View.OVER_SCROLL_NEVER);

        content.addView(pager, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    private void buildSearchPill() {
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.setGravity(Gravity.CENTER);

        dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER);
        holder.addView(dots, new LinearLayout.LayoutParams(-1, dp(18)));
        rebuildDots(0);

        TextView search = text("⌕  Search", 13, Color.WHITE);
        search.setGravity(Gravity.CENTER);
        search.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        search.setBackground(round(Color.argb(98, 18, 20, 28), 18));
        holder.addView(search, new LinearLayout.LayoutParams(dp(124), dp(34)));
        search.setOnClickListener(v -> {
            press(v);
            showSearch();
        });

        content.addView(holder, new LinearLayout.LayoutParams(-1, dp(57)));

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                rebuildDots(position);
            }
        });
    }

    private void buildDock() {
        dock = new LinearLayout(this);
        dock.setGravity(Gravity.CENTER);
        dock.setPadding(dp(10), dp(8), dp(10), dp(8));
        dock.setBackground(glassRound(30));

        for (AppItem app : chooseDockApps()) {
            FrameLayout slot = new FrameLayout(this);

            ImageView icon = new ImageView(this);
            icon.setImageDrawable(app.icon);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int size = Math.min(62, iconDp + 2);
            slot.addView(icon, new FrameLayout.LayoutParams(dp(size), dp(size), Gravity.CENTER));

            slot.setOnClickListener(v -> {
                if (!editMode) openApp(app, icon);
            });
            slot.setOnLongClickListener(v -> {
                enterEditMode();
                return true;
            });

            dock.addView(slot, new LinearLayout.LayoutParams(0, -1, 1f));
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(82));
        lp.setMargins(0, dp(5), 0, 0);
        content.addView(dock, lp);
    }

    private View appCell(AppItem app) {
        FrameLayout wrapper = new FrameLayout(this);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(2), dp(2), dp(2), dp(2));
        wrapper.addView(box, new FrameLayout.LayoutParams(-1,-1));

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int realSize = Math.max(44, Math.min(iconDp, columns == 5 ? 52 : 64));
        box.addView(icon, new LinearLayout.LayoutParams(dp(realSize), dp(realSize)));

        TextView label = text(app.label, columns == 5 ? Math.max(9,labelSp-1) : labelSp, Color.WHITE);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setShadowLayer(4,0,1,Color.argb(190,0,0,0));
        label.setVisibility(showLabels ? View.VISIBLE : View.INVISIBLE);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1, dp(22));
        llp.setMargins(dp(2),dp(3),dp(2),0);
        box.addView(label, llp);

        wrapper.setOnClickListener(v -> {
            if (!editMode) openApp(app, icon);
        });
        wrapper.setOnLongClickListener(v -> {
            enterEditMode();
            return true;
        });

        if (editMode) {
            TextView minus = text("−", 17, Color.WHITE);
            minus.setGravity(Gravity.CENTER);
            minus.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            minus.setBackground(round(Color.rgb(235, 68, 72), 12));
            FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.TOP | Gravity.RIGHT);
            mp.setMargins(0,dp(1),dp(5),0);
            wrapper.addView(minus, mp);
            minus.setOnClickListener(v -> hideApp(app));

            ObjectAnimator jiggle = ObjectAnimator.ofFloat(box, "rotation", -0.7f, 0.7f);
            jiggle.setDuration(125);
            jiggle.setRepeatCount(ValueAnimator.INFINITE);
            jiggle.setRepeatMode(ValueAnimator.REVERSE);
            jiggle.start();
        }

        return wrapper;
    }

    private void enterEditMode() {
        if (editMode) return;
        editMode = true;
        if (haptics) root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (pager != null && pager.getAdapter() != null) pager.getAdapter().notifyDataSetChanged();
        attachEditBar();
    }

    private void exitEditMode() {
        editMode = false;
        if (editBar != null && editBar.getParent() != null) root.removeView(editBar);
        editBar = null;
        if (pager != null && pager.getAdapter() != null) pager.getAdapter().notifyDataSetChanged();
    }

    private void attachEditBar() {
        if (editBar != null && editBar.getParent() != null) return;

        editBar = new LinearLayout(this);
        editBar.setGravity(Gravity.CENTER);
        editBar.setPadding(dp(5),dp(4),dp(5),dp(4));
        editBar.setBackground(round(Color.argb(225,30,33,42), 20));

        TextView customize = text("Customize", 12, Color.rgb(125,190,255));
        customize.setGravity(Gravity.CENTER);
        customize.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        editBar.addView(customize, new LinearLayout.LayoutParams(dp(92),dp(34)));

        TextView done = text("Done", 12, Color.WHITE);
        done.setGravity(Gravity.CENTER);
        done.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        done.setBackground(round(Color.rgb(55,120,245), 16));
        editBar.addView(done, new LinearLayout.LayoutParams(dp(62),dp(34)));

        customize.setOnClickListener(v -> showSettings());
        done.setOnClickListener(v -> exitEditMode());

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(165),dp(42),Gravity.TOP | Gravity.RIGHT);
        lp.setMargins(0,dp(36),dp(16),0);
        root.addView(editBar, lp);
    }

    private void hideApp(AppItem app) {
        hiddenSet.add(key(app));
        saveHidden();
        refreshVisibleApps();
        buildLauncher();
        Toast.makeText(this, app.label + " hidden", Toast.LENGTH_SHORT).show();
    }

    private void saveHidden() {
        prefs.edit().putStringSet("hidden_apps", new HashSet<>(hiddenSet)).apply();
    }

    private void buildSearchOverlay() {
        searchOverlay = new FrameLayout(this);
        searchOverlay.setVisibility(View.GONE);
        searchOverlay.setBackgroundColor(Color.argb(238, 7, 9, 14));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(24), dp(16), dp(14));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);

        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setHint("Search Apps");
        field.setHintTextColor(Color.argb(165,255,255,255));
        field.setTextColor(Color.WHITE);
        field.setTextSize(17);
        field.setPadding(dp(16),0,dp(16),0);
        field.setBackground(round(Color.argb(58,255,255,255), 22));
        top.addView(field, new LinearLayout.LayoutParams(0, dp(48), 1f));

        TextView cancel = text("Cancel", 15, Color.rgb(100,175,255));
        cancel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(76),dp(48));
        cp.setMargins(dp(6),0,0,0);
        top.addView(cancel,cp);

        panel.addView(top,new LinearLayout.LayoutParams(-1,dp(52)));

        searchGrid = new GridView(this);
        searchGrid.setNumColumns(4);
        searchGrid.setVerticalSpacing(dp(15));
        searchGrid.setHorizontalSpacing(dp(5));
        searchGrid.setPadding(0,dp(18),0,dp(10));
        searchGrid.setClipToPadding(false);
        searchGrid.setSelector(android.R.color.transparent);
        searchAdapter = new SearchAdapter();
        searchGrid.setAdapter(searchAdapter);
        panel.addView(searchGrid,new LinearLayout.LayoutParams(-1,0,1f));

        searchOverlay.addView(panel,new FrameLayout.LayoutParams(-1,-1));
        root.addView(searchOverlay,new FrameLayout.LayoutParams(-1,-1));

        field.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a) {}
            public void onTextChanged(CharSequence s,int st,int b,int c) { filterSearch(s.toString()); }
            public void afterTextChanged(android.text.Editable e) {}
        });

        searchGrid.setOnItemClickListener((p,v,pos,id) -> openApp(searchApps.get(pos),v));
        cancel.setOnClickListener(v -> hideSearch());
        searchOverlay.setTag(field);
    }

    private void showSearch() {
        searchApps.clear();
        searchApps.addAll(visibleApps);
        searchAdapter.notifyDataSetChanged();

        EditText field = (EditText) searchOverlay.getTag();
        field.setText("");
        searchOverlay.setVisibility(View.VISIBLE);
        searchOverlay.setAlpha(0f);
        searchOverlay.setTranslationY(dp(18));
        searchOverlay.animate()
                .alpha(1f).translationY(0)
                .setDuration(200)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        field.requestFocus();
    }

    private void hideSearch() {
        searchOverlay.animate()
                .alpha(0f).translationY(dp(14))
                .setDuration(150)
                .withEndAction(() -> {
                    searchOverlay.setVisibility(View.GONE);
                    searchOverlay.setTranslationY(0);
                }).start();
    }

    private void filterSearch(String text) {
        String q = text.trim().toLowerCase(Locale.ROOT);
        searchApps.clear();
        for (AppItem a : visibleApps) {
            if (q.isEmpty() || a.label.toLowerCase(Locale.ROOT).contains(q)) searchApps.add(a);
        }
        searchAdapter.notifyDataSetChanged();
    }

    private void showSettings() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16),dp(12),dp(16),dp(24));
        panel.setBackground(round(Color.rgb(22,24,31),28));
        scroll.addView(panel,new ScrollView.LayoutParams(-1,-2));

        View handle = new View(this);
        handle.setBackground(round(Color.rgb(100,103,112),3));
        LinearLayout handleRow = new LinearLayout(this);
        handleRow.setGravity(Gravity.CENTER);
        handleRow.addView(handle,new LinearLayout.LayoutParams(dp(38),dp(5)));
        panel.addView(handleRow,new LinearLayout.LayoutParams(-1,dp(20)));

        TextView title = title("Customize");
        title.setGravity(Gravity.CENTER);
        panel.addView(title,new LinearLayout.LayoutParams(-1,dp(42)));

        LinearLayout themeCard = group();
        themeCard.addView(groupTitle("Wallpaper & Theme"));
        LinearLayout themeRow1 = chipRow();
        addThemeChip(themeRow1,"System","SYSTEM",dialog);
        addThemeChip(themeRow1,"iOS Blue","BLUE",dialog);
        addThemeChip(themeRow1,"Purple","PURPLE",dialog);
        themeCard.addView(themeRow1,new LinearLayout.LayoutParams(-1,dp(45)));
        LinearLayout themeRow2 = chipRow();
        addThemeChip(themeRow2,"AMOLED","AMOLED",dialog);
        addThemeChip(themeRow2,"Graphite","GRAPHITE",dialog);
        themeCard.addView(themeRow2,new LinearLayout.LayoutParams(-1,dp(45)));
        addCard(panel,themeCard);

        LinearLayout layoutCard = group();
        layoutCard.addView(groupTitle("Home Screen"));

        LinearLayout grids = chipRow();
        addGridChip(grids,"4 × 5",4,5,dialog);
        addGridChip(grids,"5 × 6",5,6,dialog);
        layoutCard.addView(grids,new LinearLayout.LayoutParams(-1,dp(46)));

        TextView iconValue = text("Icon Size  •  " + iconDp + " dp",13,Color.WHITE);
        iconValue.setPadding(dp(10),dp(8),dp(10),0);
        layoutCard.addView(iconValue,new LinearLayout.LayoutParams(-1,dp(34)));
        SeekBar iconSeek = new SeekBar(this);
        iconSeek.setMax(24);
        iconSeek.setProgress(Math.max(0,Math.min(24,iconDp-46)));
        layoutCard.addView(iconSeek,new LinearLayout.LayoutParams(-1,dp(46)));
        iconSeek.setOnSeekBarChangeListener(new SimpleSeek() {
            public void onProgressChanged(SeekBar s,int p,boolean f) {
                iconDp=46+p;
                iconValue.setText("Icon Size  •  " + iconDp + " dp");
                prefs.edit().putInt("icon_dp",iconDp).apply();
            }
            public void onStopTrackingTouch(SeekBar s) { rebuildAfterSettings(dialog); }
        });

        TextView labelValue = text("Label Size  •  " + labelSp + " sp",13,Color.WHITE);
        labelValue.setPadding(dp(10),dp(4),dp(10),0);
        layoutCard.addView(labelValue,new LinearLayout.LayoutParams(-1,dp(32)));
        SeekBar labelSeek = new SeekBar(this);
        labelSeek.setMax(5);
        labelSeek.setProgress(Math.max(0,Math.min(5,labelSp-9)));
        layoutCard.addView(labelSeek,new LinearLayout.LayoutParams(-1,dp(46)));
        labelSeek.setOnSeekBarChangeListener(new SimpleSeek() {
            public void onProgressChanged(SeekBar s,int p,boolean f) {
                labelSp=9+p;
                labelValue.setText("Label Size  •  " + labelSp + " sp");
                prefs.edit().putInt("label_sp",labelSp).apply();
            }
            public void onStopTrackingTouch(SeekBar s) { rebuildAfterSettings(dialog); }
        });

        Switch labels = switchRow("Show App Labels",showLabels);
        layoutCard.addView(labels,new LinearLayout.LayoutParams(-1,dp(50)));
        labels.setOnCheckedChangeListener((b,checked) -> {
            showLabels=checked;
            prefs.edit().putBoolean("labels",checked).apply();
            rebuildAfterSettings(dialog);
        });

        addCard(panel,layoutCard);

        LinearLayout appearanceCard = group();
        appearanceCard.addView(groupTitle("Appearance & Touch"));
        TextView glassValue = text("Glass Strength  •  " + glassAlpha + "%",13,Color.WHITE);
        glassValue.setPadding(dp(10),dp(6),dp(10),0);
        appearanceCard.addView(glassValue,new LinearLayout.LayoutParams(-1,dp(33)));
        SeekBar glassSeek = new SeekBar(this);
        glassSeek.setMax(100);
        glassSeek.setProgress(glassAlpha);
        appearanceCard.addView(glassSeek,new LinearLayout.LayoutParams(-1,dp(46)));
        glassSeek.setOnSeekBarChangeListener(new SimpleSeek() {
            public void onProgressChanged(SeekBar s,int p,boolean f) {
                glassAlpha=Math.max(25,p);
                glassValue.setText("Glass Strength  •  " + glassAlpha + "%");
                prefs.edit().putInt("glass_alpha",glassAlpha).apply();
            }
            public void onStopTrackingTouch(SeekBar s) { rebuildAfterSettings(dialog); }
        });

        Switch haptic = switchRow("Haptic Touch",haptics);
        appearanceCard.addView(haptic,new LinearLayout.LayoutParams(-1,dp(50)));
        haptic.setOnCheckedChangeListener((b,checked) -> {
            haptics=checked;
            prefs.edit().putBoolean("haptics",checked).apply();
        });
        addCard(panel,appearanceCard);

        LinearLayout appCard = group();
        appCard.addView(groupTitle("Apps"));
        TextView dockRow = settingsRow("Customize Dock","Choose your 4 favorite apps");
        appCard.addView(dockRow,new LinearLayout.LayoutParams(-1,dp(58)));
        dockRow.setOnClickListener(v -> showDockChooser(dialog));

        TextView hiddenRow = settingsRow("Hidden Apps",hiddenSet.size() + " hidden");
        appCard.addView(hiddenRow,new LinearLayout.LayoutParams(-1,dp(58)));
        hiddenRow.setOnClickListener(v -> showHiddenApps(dialog));
        addCard(panel,appCard);

        LinearLayout systemCard = group();
        systemCard.addView(groupTitle("System"));
        TextView def = settingsRow("Set as Default Launcher","Use this launcher for Home");
        systemCard.addView(def,new LinearLayout.LayoutParams(-1,dp(58)));
        def.setOnClickListener(v -> {
            dialog.dismiss();
            try {
                startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
            } catch (Exception e) {
                Intent i=new Intent(Intent.ACTION_MAIN);
                i.addCategory(Intent.CATEGORY_HOME);
                startActivity(Intent.createChooser(i,"Choose Home app"));
            }
        });

        TextView settings = settingsRow("Android Settings","Open device settings");
        systemCard.addView(settings,new LinearLayout.LayoutParams(-1,dp(58)));
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_SETTINGS)));
        addCard(panel,systemCard);

        TextView hint = text("Home Screen app icon par long-press → Edit Mode. Empty area par long-press → Customize. Swipe down → Spotlight Search.",12,Color.argb(165,255,255,255));
        hint.setPadding(dp(8),dp(8),dp(8),0);
        panel.addView(hint,new LinearLayout.LayoutParams(-1,dp(75)));

        dialog.setContentView(scroll);
        Window w=dialog.getWindow();
        if(w!=null){
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setDimAmount(.42f);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setGravity(Gravity.BOTTOM);
        }
        dialog.setOnShowListener(d -> {
            Window ww=dialog.getWindow();
            if(ww!=null) ww.setLayout(WindowManager.LayoutParams.MATCH_PARENT,(int)(getResources().getDisplayMetrics().heightPixels*.84f));
        });
        dialog.show();
    }

    private LinearLayout group() {
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setPadding(dp(8),dp(6),dp(8),dp(8));
        g.setBackground(round(Color.rgb(36,39,48),22));
        return g;
    }

    private TextView groupTitle(String s) {
        TextView t = text(s,12,Color.rgb(130,185,255));
        t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setPadding(dp(10),dp(4),dp(10),dp(4));
        return t;
    }

    private void addCard(LinearLayout panel, View card) {
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(7),0,dp(7));
        panel.addView(card,lp);
    }

    private TextView settingsRow(String title,String subtitle) {
        TextView t=new TextView(this);
        t.setText(title + "\n" + subtitle + "   ›");
        t.setTextColor(Color.WHITE);
        t.setTextSize(14);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(12),dp(6),dp(12),dp(6));
        t.setLineSpacing(0,1.05f);
        return t;
    }

    private Switch switchRow(String label,boolean checked) {
        Switch s=new Switch(this);
        s.setText(label);
        s.setTextColor(Color.WHITE);
        s.setTextSize(14);
        s.setChecked(checked);
        s.setPadding(dp(10),0,dp(10),0);
        return s;
    }

    private void addThemeChip(LinearLayout row,String label,String value,Dialog dialog) {
        TextView chip=chip(label,theme.equals(value));
        row.addView(chip,new LinearLayout.LayoutParams(0,dp(38),1f));
        chip.setOnClickListener(v -> {
            theme=value;
            prefs.edit().putString("theme",value).apply();
            rebuildAfterSettings(dialog);
        });
    }

    private void addGridChip(LinearLayout row,String label,int c,int r,Dialog dialog) {
        TextView chip=chip(label,columns==c && rows==r);
        row.addView(chip,new LinearLayout.LayoutParams(0,dp(38),1f));
        chip.setOnClickListener(v -> {
            columns=c; rows=r;
            prefs.edit().putInt("columns",c).putInt("rows",r).apply();
            rebuildAfterSettings(dialog);
        });
    }

    private void showDockChooser(Dialog settingsDialog) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14),dp(14),dp(14),dp(14));
        panel.setBackground(round(Color.rgb(24,26,34),24));

        TextView title=title("Choose Dock Apps");
        panel.addView(title,new LinearLayout.LayoutParams(-1,dp(44)));

        ArrayList<String> selected = new ArrayList<>();
        String saved=prefs.getString("dock_apps","");
        if(!saved.isEmpty()){
            String[] parts=saved.split("\\|");
            for(String s:parts) if(!s.isEmpty()) selected.add(s);
        }

        GridView grid=new GridView(this);
        grid.setNumColumns(4);
        grid.setVerticalSpacing(dp(12));
        grid.setHorizontalSpacing(dp(5));
        ChoiceAdapter adapter=new ChoiceAdapter(installedApps,selected);
        grid.setAdapter(adapter);
        panel.addView(grid,new LinearLayout.LayoutParams(-1,0,1f));

        grid.setOnItemClickListener((p,v,pos,id) -> {
            String k=key(installedApps.get(pos));
            if(selected.contains(k)) selected.remove(k);
            else if(selected.size()<4) selected.add(k);
            else Toast.makeText(this,"Dock me maximum 4 apps",Toast.LENGTH_SHORT).show();
            adapter.notifyDataSetChanged();
        });

        Button save=actionButton("Save Dock");
        panel.addView(save,new LinearLayout.LayoutParams(-1,dp(50)));
        save.setOnClickListener(v -> {
            StringBuilder sb=new StringBuilder();
            for(int i=0;i<selected.size();i++){
                if(i>0) sb.append("|");
                sb.append(selected.get(i));
            }
            prefs.edit().putString("dock_apps",sb.toString()).apply();
            dialog.dismiss();
            settingsDialog.dismiss();
            buildLauncher();
        });

        dialog.setContentView(panel);
        Window w=dialog.getWindow();
        if(w!=null){
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.BOTTOM);
        }
        dialog.setOnShowListener(d -> {
            Window ww=dialog.getWindow();
            if(ww!=null) ww.setLayout(WindowManager.LayoutParams.MATCH_PARENT,(int)(getResources().getDisplayMetrics().heightPixels*.72f));
        });
        dialog.show();
    }

    private void showHiddenApps(Dialog settingsDialog) {
        final Dialog dialog=new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14),dp(14),dp(14),dp(14));
        panel.setBackground(round(Color.rgb(24,26,34),24));

        TextView title=title("Hidden Apps");
        panel.addView(title,new LinearLayout.LayoutParams(-1,dp(44)));

        ArrayList<String> selected=new ArrayList<>(hiddenSet);
        GridView grid=new GridView(this);
        grid.setNumColumns(4);
        grid.setVerticalSpacing(dp(12));
        ChoiceAdapter adapter=new ChoiceAdapter(installedApps,selected);
        grid.setAdapter(adapter);
        panel.addView(grid,new LinearLayout.LayoutParams(-1,0,1f));

        grid.setOnItemClickListener((p,v,pos,id) -> {
            String k=key(installedApps.get(pos));
            if(selected.contains(k)) selected.remove(k); else selected.add(k);
            adapter.notifyDataSetChanged();
        });

        Button save=actionButton("Save Hidden Apps");
        panel.addView(save,new LinearLayout.LayoutParams(-1,dp(50)));
        save.setOnClickListener(v -> {
            hiddenSet.clear();
            hiddenSet.addAll(selected);
            saveHidden();
            refreshVisibleApps();
            dialog.dismiss();
            settingsDialog.dismiss();
            buildLauncher();
        });

        dialog.setContentView(panel);
        Window w=dialog.getWindow();
        if(w!=null){
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.BOTTOM);
        }
        dialog.setOnShowListener(d -> {
            Window ww=dialog.getWindow();
            if(ww!=null) ww.setLayout(WindowManager.LayoutParams.MATCH_PARENT,(int)(getResources().getDisplayMetrics().heightPixels*.72f));
        });
        dialog.show();
    }

    private void rebuildAfterSettings(Dialog dialog) {
        dialog.dismiss();
        buildLauncher();
    }

    private LinearLayout chipRow() {
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private TextView chip(String label,boolean selected) {
        TextView t=text(label,11,Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.DEFAULT,selected?Typeface.BOLD:Typeface.NORMAL);
        t.setBackground(round(selected?Color.rgb(55,120,245):Color.rgb(56,59,70),16));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(38),1f);
        lp.setMargins(dp(3),0,dp(3),0);
        t.setLayoutParams(lp);
        return t;
    }

    private Button actionButton(String label) {
        Button b=new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setBackground(round(Color.rgb(45,110,235),18));
        return b;
    }

    private List<AppItem> chooseDockApps() {
        List<AppItem> out=new ArrayList<>();
        Set<String> used=new HashSet<>();

        String saved=prefs.getString("dock_apps","");
        if(!saved.isEmpty()){
            String[] parts=saved.split("\\|");
            for(String k:parts){
                AppItem a=findByKey(k);
                if(a!=null && !hiddenSet.contains(k) && out.size()<4){
                    out.add(a); used.add(k);
                }
            }
        }

        String[][] groups={{"phone","dialer","contacts"},{"message","messages","sms"},{"chrome","browser"},{"camera"}};
        for(String[] group:groups){
            if(out.size()>=4) break;
            AppItem m=findByKeywords(group,used);
            if(m!=null){ out.add(m); used.add(key(m)); }
        }

        for(AppItem a:visibleApps){
            if(out.size()>=4) break;
            if(!used.contains(key(a))){ out.add(a); used.add(key(a)); }
        }
        return out;
    }

    private AppItem findByKey(String k) {
        for(AppItem a:installedApps) if(key(a).equals(k)) return a;
        return null;
    }

    private AppItem findByKeywords(String[] words,Set<String> used) {
        for(String w:words){
            for(AppItem a:visibleApps){
                if(!used.contains(key(a)) && a.label.toLowerCase(Locale.ROOT).contains(w)) return a;
            }
        }
        return null;
    }

    private void updateStatus() {
        if(batteryText!=null){
            BatteryManager bm=(BatteryManager)getSystemService(BATTERY_SERVICE);
            int pct=bm==null?-1:bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            batteryText.setText(pct>=0?pct+"%":"");
        }
        if(networkText!=null) networkText.setText(networkLabel());
    }

    private String networkLabel() {
        try{
            ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
            Network n=cm.getActiveNetwork();
            if(n==null) return "";
            NetworkCapabilities c=cm.getNetworkCapabilities(n);
            if(c==null) return "";
            if(c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "Wi-Fi";
            if(c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "Mobile";
            if(c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Ethernet";
        }catch(Exception ignored){}
        return "";
    }

    private void openApp(AppItem app,View pressed) {
        if(haptics) pressed.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        pressed.animate().scaleX(.86f).scaleY(.86f).setDuration(70).withEndAction(() -> {
            pressed.animate().scaleX(1f).scaleY(1f).setDuration(150).setInterpolator(new DecelerateInterpolator()).start();
            try{
                Intent i=new Intent();
                i.setComponent(app.component);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }catch(Exception e){
                Toast.makeText(this,"App open nahi hua",Toast.LENGTH_SHORT).show();
            }
        }).start();
    }

    private void press(View v) {
        if(haptics) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        v.animate().scaleX(.93f).scaleY(.93f).setDuration(65).withEndAction(() ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(115).start()
        ).start();
    }

    private void rebuildDots(int selected) {
        if(dots==null || pager==null || pager.getAdapter()==null) return;
        dots.removeAllViews();
        int count=pager.getAdapter().getItemCount();
        if(count>8){
            TextView page=text((selected+1)+" / "+count,11,Color.argb(200,255,255,255));
            page.setGravity(Gravity.CENTER);
            dots.addView(page,new LinearLayout.LayoutParams(dp(48),dp(18)));
            return;
        }
        for(int i=0;i<count;i++){
            TextView d=new TextView(this);
            d.setText(i==selected?"●":"•");
            d.setTextColor(i==selected?Color.WHITE:Color.argb(125,255,255,255));
            d.setTextSize(i==selected?8:13);
            d.setGravity(Gravity.CENTER);
            dots.addView(d,new LinearLayout.LayoutParams(dp(13),dp(18)));
        }
    }

    private void applyWallpaper() {
        if("SYSTEM".equals(theme)){
            try{
                root.setBackground(WallpaperManager.getInstance(this).getDrawable());
                return;
            }catch(Exception ignored){}
        }

        if("AMOLED".equals(theme)){
            root.setBackground(wallpaper(new int[]{0xFF000000,0xFF000000,0xFF050505}));
        }else if("PURPLE".equals(theme)){
            root.setBackground(wallpaper(new int[]{0xFF140A2A,0xFF432074,0xFF802A78}));
        }else if("GRAPHITE".equals(theme)){
            root.setBackground(wallpaper(new int[]{0xFF080A0E,0xFF1B2430,0xFF0C1118}));
        }else{
            root.setBackground(wallpaper(new int[]{0xFF081B3B,0xFF1550B8,0xFF2088F4}));
        }
    }

    private Drawable wallpaper(int[] colors) {
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,colors);
        g.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        return g;
    }

    private Drawable glassRound(int radius) {
        boolean dark="AMOLED".equals(theme) || "GRAPHITE".equals(theme);
        int alpha=Math.max(30,Math.min(150,glassAlpha+20));
        GradientDrawable g=new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                dark
                        ? new int[]{Color.argb(alpha,34,37,45),Color.argb(Math.max(25,alpha-25),18,20,27)}
                        : new int[]{Color.argb(alpha,235,243,255),Color.argb(Math.max(24,alpha-38),120,165,225)}
        );
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(1),Color.argb(40,255,255,255));
        return g;
    }

    private GradientDrawable round(int color,int radius) {
        GradientDrawable g=new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private TextView title(String value) {
        TextView t=text(value,22,Color.WHITE);
        t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }

    private TextView text(String value,int sp,int color) {
        TextView t=new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private int dp(int n) {
        return Math.round(n*getResources().getDisplayMetrics().density);
    }

    private String key(AppItem app) {
        return app.component.flattenToString();
    }

    private final class PageAdapter extends RecyclerView.Adapter<PageHolder> {
        private final int pageSize=columns*rows;

        @Override public PageHolder onCreateViewHolder(ViewGroup parent,int viewType){
            FrameLayout f=new FrameLayout(MainActivity.this);
            f.setLayoutParams(new ViewGroup.LayoutParams(-1,-1));
            return new PageHolder(f);
        }

        @Override public void onBindViewHolder(PageHolder holder,int position){
            holder.frame.removeAllViews();

            GridLayout grid=new GridLayout(MainActivity.this);
            grid.setColumnCount(columns);
            grid.setRowCount(rows);
            grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
            grid.setUseDefaultMargins(false);
            grid.setPadding(0,dp(3),0,dp(3));

            int start=position*pageSize;
            for(int cell=0;cell<pageSize;cell++){
                int index=start+cell;
                View child=index<visibleApps.size()?appCell(visibleApps.get(index)):new View(MainActivity.this);
                GridLayout.LayoutParams lp=new GridLayout.LayoutParams(
                        GridLayout.spec(cell/columns,1,1f),
                        GridLayout.spec(cell%columns,1,1f)
                );
                lp.width=0;
                lp.height=0;
                grid.addView(child,lp);
            }
            holder.frame.addView(grid,new FrameLayout.LayoutParams(-1,-1));
        }

        @Override public int getItemCount(){
            return Math.max(1,(int)Math.ceil(visibleApps.size()/(double)pageSize));
        }
    }

    private final class SearchAdapter extends BaseAdapter {
        public int getCount(){ return searchApps.size(); }
        public Object getItem(int p){ return searchApps.get(p); }
        public long getItemId(int p){ return p; }

        public View getView(int p,View old,ViewGroup parent){
            AppItem app=searchApps.get(p);
            LinearLayout box=new LinearLayout(MainActivity.this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER);

            ImageView icon=new ImageView(MainActivity.this);
            icon.setImageDrawable(app.icon);
            int s=columns==5?48:56;
            box.addView(icon,new LinearLayout.LayoutParams(dp(s),dp(s)));

            TextView label=text(app.label,10,Color.WHITE);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            box.addView(label,new LinearLayout.LayoutParams(-1,dp(24)));
            return box;
        }
    }

    private final class ChoiceAdapter extends BaseAdapter {
        private final List<AppItem> source;
        private final List<String> selected;

        ChoiceAdapter(List<AppItem> source,List<String> selected){
            this.source=source;
            this.selected=selected;
        }

        public int getCount(){ return source.size(); }
        public Object getItem(int p){ return source.get(p); }
        public long getItemId(int p){ return p; }

        public View getView(int p,View old,ViewGroup parent){
            AppItem app=source.get(p);
            boolean checked=selected.contains(key(app));

            LinearLayout box=new LinearLayout(MainActivity.this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER);
            box.setAlpha(checked?1f:.55f);

            ImageView icon=new ImageView(MainActivity.this);
            icon.setImageDrawable(app.icon);
            box.addView(icon,new LinearLayout.LayoutParams(dp(52),dp(52)));

            TextView label=text((checked?"✓ ":"")+app.label,10,Color.WHITE);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            box.addView(label,new LinearLayout.LayoutParams(-1,dp(24)));
            return box;
        }
    }

    private static final class PageHolder extends RecyclerView.ViewHolder {
        final FrameLayout frame;
        PageHolder(FrameLayout itemView){
            super(itemView);
            frame=itemView;
        }
    }

    private static final class AppItem {
        final String label;
        final ComponentName component;
        final Drawable icon;

        AppItem(String label,ComponentName component,Drawable icon){
            this.label=label;
            this.component=component;
            this.icon=icon;
        }
    }

    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        public void onStartTrackingTouch(SeekBar seekBar){}
    }
}
