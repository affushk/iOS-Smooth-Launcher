package com.altaf.ioslauncher;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.ClipData;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.DragEvent;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
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
    private StatusIconsView statusIconsView;
    private FrameLayout searchOverlay;
    private GridView searchGrid;
    private SearchAdapter searchAdapter;
    private LinearLayout editBar;

    private final Set<String> hiddenSet = new HashSet<>();
    private boolean editMode = false;
    private AppItem draggingApp = null;

    private int columns;
    private int rows;
    private int iconDp;
    private int labelSp;
    private int glassAlpha;
    private int iconCornerDp;
    private String iconPackPackage;
    private boolean showLabels;
    private boolean haptics;
    private boolean showDateWidget;
    private boolean designerMode;
    private String theme;

    private float touchDownX;
    private float touchDownY;
    private boolean gestureConsumed = false;

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
        int action = ev.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            touchDownX = ev.getX();
            touchDownY = ev.getY();
            gestureConsumed = false;
            return super.dispatchTouchEvent(ev);
        }

        float dx = ev.getX() - touchDownX;
        float dy = ev.getY() - touchDownY;

        // Only the very top edge can open system panels.
        boolean topEdgeStart = touchDownY <= dp(42);
        boolean verticalPull = dy > dp(34) && Math.abs(dx) < dp(90);

        if (action == MotionEvent.ACTION_MOVE && !gestureConsumed && !editMode
                && topEdgeStart && verticalPull) {
            gestureConsumed = true;

            // Cancel the app-icon touch before it can become a click.
            MotionEvent cancel = MotionEvent.obtain(ev);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(cancel);
            cancel.recycle();
            return true;
        }

        if (action == MotionEvent.ACTION_UP && gestureConsumed) {
            if (dy > dp(105)) {
                if (touchDownX > getResources().getDisplayMetrics().widthPixels * .58f) {
                    IOSSystemPanels.showControlCenter(this, root, content, haptics, this::showSettings);
                } else {
                    IOSSystemPanels.showNotificationCenter(this, root, content, haptics);
                }
            }
            gestureConsumed = false;
            return true;
        }

        if (action == MotionEvent.ACTION_CANCEL) {
            gestureConsumed = false;
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
        showDateWidget = prefs.getBoolean("date_widget", false);
        designerMode = prefs.getBoolean("designer_mode", true);
        glassAlpha = prefs.getInt("glass_alpha", 70);
        iconCornerDp = prefs.getInt("icon_corner_dp", 14);
        iconPackPackage = prefs.getString("icon_pack", "");

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
        applySavedHomeOrder();
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

    private void applySavedHomeOrder() {
        String saved = prefs.getString("home_order", "");
        if (saved.isEmpty()) return;
        final ArrayList<String> order = new ArrayList<>();
        for (String s : saved.split("\\|")) if (!s.isEmpty()) order.add(s);
        Collections.sort(installedApps, (a,b) -> {
            int ia=order.indexOf(key(a)), ib=order.indexOf(key(b));
            if (ia<0 && ib<0) return a.label.compareToIgnoreCase(b.label);
            if (ia<0) return 1;
            if (ib<0) return -1;
            return Integer.compare(ia,ib);
        });
    }

    private void saveHomeOrder() {
        StringBuilder sb=new StringBuilder();
        for (AppItem a:visibleApps) {
            if (sb.length()>0) sb.append("|");
            sb.append(key(a));
        }
        prefs.edit().putString("home_order",sb.toString()).apply();
    }

    private void moveApp(AppItem from, AppItem to) {
        int a=visibleApps.indexOf(from), b=visibleApps.indexOf(to);
        if(a<0 || b<0 || a==b) return;
        visibleApps.remove(a);
        if (b > visibleApps.size()) b=visibleApps.size();
        visibleApps.add(b,from);
        saveHomeOrder();
        if(pager!=null && pager.getAdapter()!=null) pager.getAdapter().notifyDataSetChanged();
        rebuildDots(pager==null?0:pager.getCurrentItem());
        if(haptics && root!=null) root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private void buildLauncher() {
        root = new FrameLayout(this);
        applyWallpaper();

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(10), dp(16), dp(12));
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        buildStatusBar();
        if (showDateWidget) buildDateCard();
        if (designerMode) buildDesignerBar();
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
        root.setScaleX(.965f);
        root.setScaleY(.965f);
        root.setTranslationY(dp(8));
        root.animate()
                .alpha(1f).scaleX(1f).scaleY(1f).translationY(0)
                .setDuration(360)
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

        statusIconsView = new StatusIconsView();
        bar.addView(statusIconsView, new LinearLayout.LayoutParams(dp(94), dp(28)));

        content.addView(bar, new LinearLayout.LayoutParams(-1, dp(30)));
    }

    private void buildDateCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(15), dp(7), dp(15), dp(6));
        card.setBackground(glassRound(24));

        TextClock day = new TextClock(this);
        day.setFormat12Hour("EEEE");
        day.setFormat24Hour("EEEE");
        day.setTextColor(Color.WHITE);
        day.setTextSize(17);
        day.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(day, new LinearLayout.LayoutParams(-1, dp(26)));

        TextClock date = new TextClock(this);
        date.setFormat12Hour("d MMMM");
        date.setFormat24Hour("d MMMM");
        date.setTextColor(Color.argb(205,255,255,255));
        date.setTextSize(12);
        card.addView(date, new LinearLayout.LayoutParams(-1, dp(20)));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(190), dp(58));
        lp.setMargins(0, dp(5), 0, dp(7));
        content.addView(card, lp);
    }

    private void buildDesignerBar() {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(7),dp(5),dp(7),dp(5));
        card.setBackground(glassRound(22));
        String[] names={"Gallery","Files","Camera","Colors"};
        String[] keys={"gallery","files","camera","colors"};
        for(int i=0;i<names.length;i++){
            final String key=keys[i];
            TextView b=text(names[i],11,Color.WHITE);
            b.setGravity(Gravity.CENTER);
            b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            card.addView(b,new LinearLayout.LayoutParams(0,dp(38),1f));
            b.setOnClickListener(v -> openDesignerTool(key));
        }
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));
        lp.setMargins(0,dp(3),0,dp(5));
        content.addView(card,lp);
    }

    private void openDesignerTool(String key) {
        try {
            if ("gallery".equals(key)) {
                Intent i=new Intent(Intent.ACTION_VIEW);
                i.setType("image/*");
                startActivity(Intent.createChooser(i,"Choose Gallery"));
            } else if ("files".equals(key)) {
                Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.setType("*/*"); i.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(i,701);
            } else if ("camera".equals(key)) {
                Intent i=new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                startActivity(i);
            } else {
                showColorPalette();
            }
        } catch(Exception e) { Toast.makeText(this,"App available nahi hai",Toast.LENGTH_SHORT).show(); }
    }

    private void showColorPalette() {
        final Dialog d=new Dialog(this); d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout p=new LinearLayout(this); p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(18),dp(16),dp(18),dp(18)); p.setBackground(round(Color.rgb(24,26,34),24));
        TextView h=title("Textile Quick Colors"); p.addView(h,new LinearLayout.LayoutParams(-1,dp(48)));
        String[][] colors={{"Navy","#14213D"},{"Maroon","#7A1F35"},{"Petrol","#0F4C5C"},{"Bottle Green","#174A3A"},{"Rust","#A14D2A"},{"Mustard","#C99720"},{"Ivory","#F3EBDD"},{"Black","#111111"}};
        for(String[] x:colors){
            TextView row=settingsRow(x[0],x[1]+"  • tap to copy");
            row.setBackground(round(Color.parseColor(x[1]),16)); p.addView(row,new LinearLayout.LayoutParams(-1,dp(54)));
            row.setOnClickListener(v->{ android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE); cm.setPrimaryClip(android.content.ClipData.newPlainText("Textile color",x[1])); Toast.makeText(this,x[1]+" copied",Toast.LENGTH_SHORT).show(); });
        }
        d.setContentView(p); Window w=d.getWindow(); if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setGravity(Gravity.BOTTOM);}
        d.setOnShowListener(x->{Window ww=d.getWindow();if(ww!=null)ww.setLayout(WindowManager.LayoutParams.MATCH_PARENT,(int)(getResources().getDisplayMetrics().heightPixels*.72f));});
        d.show();
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
        inner.setVerticalScrollBarEnabled(false);
        inner.setHorizontalScrollBarEnabled(false);

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
            icon.setImageDrawable(displayIcon(app));
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            styleIcon(icon);
            int size = Math.min(62, iconDp + 2);
            slot.addView(icon, new FrameLayout.LayoutParams(dp(size), dp(size), Gravity.CENTER));

            slot.setOnClickListener(v -> {
                if (!editMode) openApp(app, icon);
            });
            slot.setOnLongClickListener(v -> {
                slot.animate().scaleX(.90f).scaleY(.90f).setDuration(90).withEndAction(() -> slot.animate().scaleX(1f).scaleY(1f).setDuration(150).start()).start();
                showAppActions(app);
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
        icon.setImageDrawable(displayIcon(app));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            styleIcon(icon);
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
            wrapper.animate().scaleX(.90f).scaleY(.90f).setDuration(90).withEndAction(() -> wrapper.animate().scaleX(1f).scaleY(1f).setDuration(150).start()).start();
            if (editMode) {
                draggingApp=app;
                ClipData data=ClipData.newPlainText("app",key(app));
                v.startDragAndDrop(data,new View.DragShadowBuilder(v),app,0);
                v.setAlpha(.45f);
            } else {
                showAppActions(app);
            }
            return true;
        });
        wrapper.setOnDragListener((v,event) -> {
            switch(event.getAction()) {
                case DragEvent.ACTION_DRAG_ENTERED:
                    v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(90).start();
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(90).start();
                    return true;
                case DragEvent.ACTION_DROP:
                    Object state=event.getLocalState();
                    if(state instanceof AppItem) moveApp((AppItem)state,app);
                    v.animate().scaleX(1f).scaleY(1f).setDuration(90).start();
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    v.setAlpha(1f);
                    draggingApp=null;
                    return true;
            }
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
            jiggle.setDuration(105);
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
        searchOverlay.setBackgroundColor(Color.argb(125, 0, 0, 0));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(22), dp(18), dp(16));

        TextView heading = text("Search", 29, Color.WHITE);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        panel.addView(heading, new LinearLayout.LayoutParams(-1, dp(47)));

        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setPadding(dp(13), 0, dp(8), 0);
        searchBar.setBackground(round(Color.argb(150, 54, 57, 66), 17));

        TextView magnify = text("⌕", 21, Color.argb(210,255,255,255));
        magnify.setGravity(Gravity.CENTER);
        searchBar.addView(magnify, new LinearLayout.LayoutParams(dp(35), dp(45)));

        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setHint("Search");
        field.setHintTextColor(Color.argb(175,255,255,255));
        field.setTextColor(Color.WHITE);
        field.setTextSize(17);
        field.setPadding(0,0,dp(4),0);
        field.setBackgroundColor(Color.TRANSPARENT);
        searchBar.addView(field, new LinearLayout.LayoutParams(0, dp(45), 1f));

        TextView cancel = text("Cancel", 15, Color.rgb(92,175,255));
        cancel.setGravity(Gravity.CENTER);
        searchBar.addView(cancel, new LinearLayout.LayoutParams(dp(70), dp(45)));

        panel.addView(searchBar, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView suggested = text("SUGGESTIONS", 11, Color.argb(165,255,255,255));
        suggested.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        suggested.setPadding(dp(3), dp(14), 0, dp(4));
        panel.addView(suggested, new LinearLayout.LayoutParams(-1, dp(40)));

        LinearLayout suggestionRow = new LinearLayout(this);
        suggestionRow.setGravity(Gravity.CENTER);
        int suggestCount = Math.min(4, visibleApps.size());
        for (int i=0;i<suggestCount;i++) {
            AppItem app=visibleApps.get(i);
            LinearLayout cell=new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            ImageView icon=new ImageView(this);
            icon.setImageDrawable(displayIcon(app));
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            styleIcon(icon);
            cell.addView(icon,new LinearLayout.LayoutParams(dp(50),dp(50)));
            TextView label=text(app.label,10,Color.WHITE);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            cell.addView(label,new LinearLayout.LayoutParams(-1,dp(23)));
            suggestionRow.addView(cell,new LinearLayout.LayoutParams(0,dp(80),1f));
            cell.setOnClickListener(v -> {
                hideSearch();
                v.postDelayed(() -> openApp(app,icon),150);
            });
        }
        panel.addView(suggestionRow,new LinearLayout.LayoutParams(-1,dp(82)));

        searchGrid = new GridView(this);
        searchGrid.setNumColumns(1);
        searchGrid.setVerticalSpacing(dp(14));
        searchGrid.setHorizontalSpacing(dp(5));
        searchGrid.setPadding(0,dp(8),0,dp(8));
        searchGrid.setClipToPadding(false);
        searchGrid.setSelector(android.R.color.transparent);
        searchGrid.setVerticalScrollBarEnabled(false);
        searchGrid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        searchAdapter = new SearchAdapter();
        searchGrid.setAdapter(searchAdapter);
        searchGrid.setVisibility(View.GONE);
        panel.addView(searchGrid,new LinearLayout.LayoutParams(-1,0,1f));

        searchOverlay.addView(panel,new FrameLayout.LayoutParams(-1,-1));
        root.addView(searchOverlay,new FrameLayout.LayoutParams(-1,-1));

        field.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a) {}
            public void onTextChanged(CharSequence s,int st,int b,int c) { filterSearch(s.toString()); }
            public void afterTextChanged(android.text.Editable e) {}
        });

        searchGrid.setOnItemClickListener((p,v,pos,id) -> {
            AppItem app=searchApps.get(pos);
            hideSearch();
            v.postDelayed(() -> openApp(app,v),150);
        });
        cancel.setOnClickListener(v -> hideSearch());
        searchOverlay.setTag(field);
    }

    private void showSearch() {
        if (searchOverlay == null || searchOverlay.getVisibility() == View.VISIBLE) return;
        searchApps.clear();
        searchApps.addAll(visibleApps);
        searchAdapter.notifyDataSetChanged();

        EditText field = (EditText) searchOverlay.getTag();
        field.setText("");
        setHomeBlur(true);

        searchOverlay.setVisibility(View.VISIBLE);
        searchOverlay.setAlpha(0f);
        searchOverlay.setTranslationY(dp(22));
        searchOverlay.animate()
                .alpha(1f).translationY(0)
                .setDuration(235)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        field.requestFocus();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void hideSearch() {
        if (searchOverlay == null || searchOverlay.getVisibility() != View.VISIBLE) return;
        searchOverlay.animate()
                .alpha(0f).translationY(dp(18))
                .setDuration(175)
                .withEndAction(() -> {
                    searchOverlay.setVisibility(View.GONE);
                    searchOverlay.setTranslationY(0);
                    setHomeBlur(false);
                }).start();
    }

    private void filterSearch(String text) {
        String q = text.trim().toLowerCase(Locale.ROOT);
        searchApps.clear();

        if (q.isEmpty()) {
            searchGrid.setVisibility(View.GONE);
            searchAdapter.notifyDataSetChanged();
            return;
        }

        for (AppItem a : visibleApps) {
            if (a.label.toLowerCase(Locale.ROOT).contains(q)) searchApps.add(a);
        }

        searchGrid.setVisibility(View.VISIBLE);
        searchAdapter.notifyDataSetChanged();
    }

    private void showSettings() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.TRANSPARENT);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

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

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = title("Settings");
        title.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(title,new LinearLayout.LayoutParams(0,dp(44),1f));

        TextView doneSettings = text("Done",14,Color.rgb(100,175,255));
        doneSettings.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        doneSettings.setGravity(Gravity.CENTER);
        titleRow.addView(doneSettings,new LinearLayout.LayoutParams(dp(68),dp(40)));
        doneSettings.setOnClickListener(v -> animateDialogClose(dialog,null));

        panel.addView(titleRow,new LinearLayout.LayoutParams(-1,dp(48)));

        LinearLayout themeCard = group();
        themeCard.addView(groupTitle("Appearance"));
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

        TextView cornerValue = text("Icon Corner  •  " + iconCornerDp + " dp",13,Color.WHITE);
        cornerValue.setPadding(dp(10),dp(4),dp(10),0);
        layoutCard.addView(cornerValue,new LinearLayout.LayoutParams(-1,dp(32)));
        SeekBar cornerSeek = new SeekBar(this);
        cornerSeek.setMax(8);
        cornerSeek.setProgress(Math.max(0,Math.min(8,iconCornerDp-14)));
        layoutCard.addView(cornerSeek,new LinearLayout.LayoutParams(-1,dp(46)));
        cornerSeek.setOnSeekBarChangeListener(new SimpleSeek() {
            public void onProgressChanged(SeekBar s,int p,boolean f) {
                iconCornerDp=14+p;
                cornerValue.setText("Icon Corner  •  " + iconCornerDp + " dp");
                prefs.edit().putInt("icon_corner_dp",iconCornerDp).apply();
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

        Switch dateWidget = switchRow("Date Widget",showDateWidget);
        layoutCard.addView(dateWidget,new LinearLayout.LayoutParams(-1,dp(50)));
        dateWidget.setOnCheckedChangeListener((b,checked) -> {
            showDateWidget=checked;
            prefs.edit().putBoolean("date_widget",checked).apply();
            rebuildAfterSettings(dialog);
        });

        Switch designer = switchRow("Designer Workspace",designerMode);
        layoutCard.addView(designer,new LinearLayout.LayoutParams(-1,dp(50)));
        designer.setOnCheckedChangeListener((b,checked) -> {
            designerMode=checked;
            prefs.edit().putBoolean("designer_mode",checked).apply();
            rebuildAfterSettings(dialog);
        });

        addCard(panel,layoutCard);

        LinearLayout designerCard=group();
        designerCard.addView(groupTitle("Designer Tools"));
        TextView paletteRow=settingsRow("Textile Quick Colors","Navy, maroon, petrol, rust & more");
        designerCard.addView(paletteRow,new LinearLayout.LayoutParams(-1,dp(58)));
        paletteRow.setOnClickListener(v->{ dialog.dismiss(); showColorPalette(); });
        TextView filesRow=settingsRow("Open Design File","Quick access to artwork and references");
        designerCard.addView(filesRow,new LinearLayout.LayoutParams(-1,dp(58)));
        filesRow.setOnClickListener(v->{ dialog.dismiss(); openDesignerTool("files"); });
        addCard(panel,designerCard);

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

        TextView iconPackRow = settingsRow("Icon Pack", iconPackPackage == null || iconPackPackage.isEmpty()
                ? "Default app icons"
                : IconPackManager.labelForPackage(this,iconPackPackage));
        appCard.addView(iconPackRow,new LinearLayout.LayoutParams(-1,dp(58)));
        iconPackRow.setOnClickListener(v -> showIconPackChooser(dialog));

        TextView dockRow = settingsRow("Customize Dock","Choose your 4 favorite apps");
        appCard.addView(dockRow,new LinearLayout.LayoutParams(-1,dp(58)));
        dockRow.setOnClickListener(v -> showDockChooser(dialog));

        TextView hiddenRow = settingsRow("Hidden Apps",hiddenSet.size() + " hidden");
        appCard.addView(hiddenRow,new LinearLayout.LayoutParams(-1,dp(58)));
        hiddenRow.setOnClickListener(v -> showHiddenApps(dialog));
        addCard(panel,appCard);

        LinearLayout phoneCard = group();
        phoneCard.addView(groupTitle("Phone"));

        TextView wifiSettings = settingsRow("Wi‑Fi","Networks & internet");
        phoneCard.addView(wifiSettings,new LinearLayout.LayoutParams(-1,dp(58)));
        wifiSettings.setOnClickListener(v -> {
            dialog.dismiss();
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
                    startActivity(new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY));
                else startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
            } catch(Exception ignored) {}
        });

        TextView btSettings = settingsRow("Bluetooth","Devices & connections");
        phoneCard.addView(btSettings,new LinearLayout.LayoutParams(-1,dp(58)));
        btSettings.setOnClickListener(v -> {
            dialog.dismiss();
            try { startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); } catch(Exception ignored) {}
        });

        TextView displaySettings = settingsRow("Display & Brightness","Brightness and screen");
        phoneCard.addView(displaySettings,new LinearLayout.LayoutParams(-1,dp(58)));
        displaySettings.setOnClickListener(v -> {
            dialog.dismiss();
            try { startActivity(new Intent(Settings.ACTION_DISPLAY_SETTINGS)); } catch(Exception ignored) {}
        });

        TextView soundSettings = settingsRow("Sounds & Haptics","Volume and sound");
        phoneCard.addView(soundSettings,new LinearLayout.LayoutParams(-1,dp(58)));
        soundSettings.setOnClickListener(v -> {
            dialog.dismiss();
            try { startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS)); } catch(Exception ignored) {}
        });

        TextView notificationSettings = settingsRow("Notifications","Notification access & settings");
        phoneCard.addView(notificationSettings,new LinearLayout.LayoutParams(-1,dp(58)));
        notificationSettings.setOnClickListener(v -> {
            dialog.dismiss();
            try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); } catch(Exception ignored) {}
        });

        addCard(panel,phoneCard);

        LinearLayout systemCard = group();
        systemCard.addView(groupTitle("System"));
        TextView def = settingsRow("Set as Default Launcher","Use this launcher for Home");
        systemCard.addView(def,new LinearLayout.LayoutParams(-1,dp(58)));
        def.setOnClickListener(v -> animateDialogClose(dialog,() -> {
            try {
                startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
            } catch (Exception e) {
                Intent i=new Intent(Intent.ACTION_MAIN);
                i.addCategory(Intent.CATEGORY_HOME);
                startActivity(Intent.createChooser(i,"Choose Home app"));
            }
        }));

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
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnKeyListener((d,key,event) -> {
            if(key==KeyEvent.KEYCODE_BACK && event.getAction()==KeyEvent.ACTION_UP){
                animateDialogClose(dialog,null);
                return true;
            }
            return false;
        });
        dialog.setOnShowListener(d -> {
            Window ww=dialog.getWindow();
            if(ww!=null) ww.setLayout(WindowManager.LayoutParams.MATCH_PARENT,(int)(getResources().getDisplayMetrics().heightPixels*.84f));
        });
        dialog.show();
        animateDialogOpen(dialog);
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
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
        };
        s.setThumbTintList(new ColorStateList(states, new int[]{Color.WHITE, Color.rgb(220,220,225)}));
        s.setTrackTintList(new ColorStateList(states, new int[]{Color.rgb(52,199,89), Color.rgb(98,98,105)}));
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
        grid.setVerticalScrollBarEnabled(false);
        grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
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
            animateDialogClose(dialog,() -> animateDialogClose(settingsDialog,this::buildLauncher));
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

    private void showIconPackChooser(Dialog settingsDialog) {
        final Dialog dialog=new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14),dp(14),dp(14),dp(14));
        panel.setBackground(round(Color.rgb(24,26,34),24));

        TextView title=title("Icon Packs");
        panel.addView(title,new LinearLayout.LayoutParams(-1,dp(44)));

        TextView info=text("Compatible installed icon packs yahan dikhte hain. Play Store se iOS-style icon pack install karke phir yahan select karo.",12,Color.argb(175,255,255,255));
        info.setPadding(0,0,0,dp(8));
        panel.addView(info,new LinearLayout.LayoutParams(-1,dp(58)));

        TextView defaultRow=settingsRow("Default Icons","Use original app icons");
        panel.addView(defaultRow,new LinearLayout.LayoutParams(-1,dp(58)));
        defaultRow.setOnClickListener(v -> {
            prefs.edit().putString("icon_pack","").apply();
            iconPackPackage="";
            animateDialogClose(dialog,() -> animateDialogClose(settingsDialog,this::buildLauncher));
        });

        List<IconPackManager.Pack> packs=IconPackManager.discover(this);
        if(packs.isEmpty()){
            TextView empty=text("Koi compatible icon pack detect nahi hua. Play Store se icon pack install karke launcher dubara open karo.",13,Color.argb(190,255,255,255));
            empty.setPadding(dp(8),dp(14),dp(8),dp(14));
            panel.addView(empty,new LinearLayout.LayoutParams(-1,dp(86)));
        }else{
            ScrollView listScroll=new ScrollView(this);
            listScroll.setVerticalScrollBarEnabled(false);
            LinearLayout list=new LinearLayout(this);
            list.setOrientation(LinearLayout.VERTICAL);

            for(IconPackManager.Pack pack:packs){
                LinearLayout row=new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(8),dp(4),dp(8),dp(4));

                ImageView icon=new ImageView(this);
                icon.setImageDrawable(pack.icon);
                styleIcon(icon);
                row.addView(icon,new LinearLayout.LayoutParams(dp(44),dp(44)));

                TextView label=text(pack.label,14,Color.WHITE);
                label.setPadding(dp(12),0,0,0);
                row.addView(label,new LinearLayout.LayoutParams(0,dp(52),1f));

                if(pack.packageName.equals(iconPackPackage)){
                    TextView check=text("✓",17,Color.rgb(52,199,89));
                    check.setGravity(Gravity.CENTER);
                    row.addView(check,new LinearLayout.LayoutParams(dp(36),dp(52)));
                }

                row.setOnClickListener(v -> {
                    prefs.edit().putString("icon_pack",pack.packageName).apply();
                    iconPackPackage=pack.packageName;
                    animateDialogClose(dialog,() -> animateDialogClose(settingsDialog,this::buildLauncher));
                });
                list.addView(row,new LinearLayout.LayoutParams(-1,dp(58)));
            }
            listScroll.addView(list,new ScrollView.LayoutParams(-1,-2));
            panel.addView(listScroll,new LinearLayout.LayoutParams(-1,0,1f));
        }

        dialog.setContentView(panel);
        Window w=dialog.getWindow();
        if(w!=null){
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.BOTTOM);
        }
        dialog.setOnShowListener(d -> {
            Window ww=dialog.getWindow();
            if(ww!=null) ww.setLayout(WindowManager.LayoutParams.MATCH_PARENT,(int)(getResources().getDisplayMetrics().heightPixels*.68f));
        });
        dialog.show();
        animateDialogOpen(dialog);
    }

    private Drawable displayIcon(AppItem app) {
        Drawable base = IconPackManager.iconFor(this,iconPackPackage,app.component,app.icon);
        float ratio = Math.max(.24f, Math.min(.32f, iconCornerDp / 64f));
        return new IOSIconDrawable(base, ratio, app.label + " " + app.component.getPackageName());
    }

    private void styleIcon(ImageView icon) {
        icon.setClipToOutline(false);
        icon.setPadding(0,0,0,0);
    }

    private void animateDialogOpen(Dialog dialog) {
        Window w=dialog.getWindow();
        if(w==null) return;
        View decor=w.getDecorView();
        decor.setAlpha(0f);
        decor.setTranslationY(dp(90));
        decor.setScaleX(.98f);
        decor.setScaleY(.98f);
        decor.animate()
                .alpha(1f)
                .translationY(0)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(260)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void animateDialogClose(Dialog dialog,Runnable after) {
        if(dialog==null || !dialog.isShowing()){
            if(after!=null) after.run();
            return;
        }
        Window w=dialog.getWindow();
        if(w==null){
            dialog.dismiss();
            if(after!=null) after.run();
            return;
        }
        View decor=w.getDecorView();
        decor.animate()
                .alpha(0f)
                .translationY(dp(110))
                .scaleX(.985f)
                .scaleY(.985f)
                .setDuration(210)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    try { dialog.dismiss(); } catch(Exception ignored) {}
                    if(after!=null) after.run();
                })
                .start();
    }

    private void rebuildAfterSettings(Dialog dialog) {
        animateDialogClose(dialog,this::buildLauncher);
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
        if (statusIconsView != null) statusIconsView.invalidate();
    }

    private void openApp(AppItem app,View pressed) {
        String pkg = app.component.getPackageName().toLowerCase(Locale.ROOT);
        String lbl = app.label.toLowerCase(Locale.ROOT);

        // Keep normal launcher settings inside our iOS-style Settings hub.
        if ("settings".equals(lbl) || pkg.contains("settings")) {
            press(pressed);
            showSettings();
            return;
        }

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

    private void showAppActions(AppItem app) {
        IOSSystemPanels.showAppActions(
                this,
                root,
                content,
                app.label,
                displayIcon(app),
                () -> openApp(app, root),
                () -> addAppToDock(app),
                () -> {
                    enterEditMode();
                    Toast.makeText(this,"Icon ko long-press karke drag karo",Toast.LENGTH_SHORT).show();
                },
                () -> exportApk(app),
                () -> hideApp(app),
                () -> openAppInfo(app),
                () -> uninstallApp(app),
                haptics
        );
    }

    private void addAppToDock(AppItem app) {
        ArrayList<String> keys = new ArrayList<>();
        String saved = prefs.getString("dock_apps","");
        if (!saved.isEmpty()) {
            String[] parts = saved.split("\\|");
            for (String s : parts) if (!s.isEmpty() && !s.equals(key(app))) keys.add(s);
        }
        keys.add(key(app));
        while (keys.size() > 4) keys.remove(0);

        StringBuilder sb=new StringBuilder();
        for (int i=0;i<keys.size();i++) {
            if (i>0) sb.append("|");
            sb.append(keys.get(i));
        }
        prefs.edit().putString("dock_apps",sb.toString()).apply();
        buildLauncher();
        Toast.makeText(this,app.label+" added to Dock",Toast.LENGTH_SHORT).show();
    }

    private void exportApk(AppItem app) {
        try {
            android.content.pm.ApplicationInfo ai = getPackageManager().getApplicationInfo(app.component.getPackageName(), 0);
            java.io.File src = new java.io.File(ai.sourceDir);
            java.io.File dir = new java.io.File(getExternalCacheDir(), "exported_apks");
            if (!dir.exists()) dir.mkdirs();
            String safe = app.label.replaceAll("[^a-zA-Z0-9._-]", "_");
            java.io.File out = new java.io.File(dir, safe + ".apk");
            try (java.io.InputStream in = new java.io.FileInputStream(src);
                 java.io.OutputStream os = new java.io.FileOutputStream(out)) {
                byte[] buf = new byte[65536]; int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            }
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName()+".files", out);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/vnd.android.package-archive");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Share / Save APK"));
        } catch (Exception e) {
            Toast.makeText(this, "Is app ka APK export nahi ho saka", Toast.LENGTH_SHORT).show();
        }
    }

    private void openAppInfo(AppItem app) {
        try {
            Intent i=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:"+app.component.getPackageName()));
            startActivity(i);
        } catch(Exception ignored) {}
    }

    private void uninstallApp(AppItem app) {
        try {
            startActivity(new Intent(Intent.ACTION_DELETE,
                    Uri.parse("package:"+app.component.getPackageName())));
        } catch(Exception ignored) {}
    }

    private void setHomeBlur(boolean enabled) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (enabled) {
                content.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(
                        18f,18f,android.graphics.Shader.TileMode.CLAMP));
            } else {
                content.setRenderEffect(null);
            }
        } else {
            content.setAlpha(enabled ? .62f : 1f);
        }
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

    private float dp(float n) {
        return n*getResources().getDisplayMetrics().density;
    }

    private String key(AppItem app) {
        return app.component.flattenToString();
    }

    private final class StatusIconsView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        StatusIconsView() {
            super(MainActivity.this);
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(dp(1));
            paint.setStrokeCap(Paint.Cap.ROUND);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            paint.setColor(Color.WHITE);

            // Cellular bars
            float x = dp(1);
            float base = dp(20);
            for (int i=0;i<4;i++) {
                float h = dp(4 + i*3);
                canvas.drawRoundRect(x + dp(i*5), base-h, x + dp(i*5+3), base, dp(1), dp(1), paint);
            }

            // Wi-Fi arcs
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.6f));
            float wx = dp(29);
            float wy = dp(13);
            for (int i=0;i<3;i++) {
                float r = dp(4 + i*3);
                rect.set(wx-r, wy-r, wx+r, wy+r);
                canvas.drawArc(rect, 215, 110, false, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(wx, dp(17), dp(1.3f), paint);

            // Battery
            BatteryManager bm=(BatteryManager)getSystemService(BATTERY_SERVICE);
            int pct=bm==null?0:bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            float bx=dp(48), by=dp(7), bw=dp(27), bh=dp(13);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.2f));
            rect.set(bx,by,bx+bw,by+bh);
            canvas.drawRoundRect(rect,dp(3),dp(3),paint);
            canvas.drawRoundRect(bx+bw+dp(1),by+dp(4),bx+bw+dp(3),by+bh-dp(4),dp(1),dp(1),paint);
            paint.setStyle(Paint.Style.FILL);
            float fill=Math.max(0,Math.min(1,pct/100f));
            rect.set(bx+dp(2),by+dp(2),bx+dp(2)+(bw-dp(4))*fill,by+bh-dp(2));
            canvas.drawRoundRect(rect,dp(2),dp(2),paint);

            paint.setTextSize(dp(8));
            paint.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(pct > 55 ? Color.BLACK : Color.WHITE);
            canvas.drawText(String.valueOf(pct), bx + bw/2f, by + dp(10), paint);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setColor(Color.WHITE);
        }
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

            LinearLayout row=new LinearLayout(MainActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12),dp(5),dp(12),dp(5));
            row.setBackground(round(Color.argb(74,255,255,255),18));

            ImageView icon=new ImageView(MainActivity.this);
            icon.setImageDrawable(displayIcon(app));
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            styleIcon(icon);
            row.addView(icon,new LinearLayout.LayoutParams(dp(48),dp(48)));

            TextView label=text(app.label,15,Color.WHITE);
            label.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            label.setPadding(dp(12),0,0,0);
            row.addView(label,new LinearLayout.LayoutParams(0,dp(56),1f));

            TextView arrow=text("›",24,Color.argb(170,255,255,255));
            arrow.setGravity(Gravity.CENTER);
            row.addView(arrow,new LinearLayout.LayoutParams(dp(30),dp(56)));

            GridView.LayoutParams gp=new GridView.LayoutParams(-1,dp(64));
            row.setLayoutParams(gp);
            return row;
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
            icon.setImageDrawable(displayIcon(app));
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            styleIcon(icon);
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
