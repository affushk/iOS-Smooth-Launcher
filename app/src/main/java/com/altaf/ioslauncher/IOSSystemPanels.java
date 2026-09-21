package com.altaf.ioslauncher;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextClock;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class IOSSystemPanels {

    private IOSSystemPanels() {}

    public static void showControlCenter(Activity activity, FrameLayout root, View home, boolean haptics, Runnable settingsAction) {
        if (findOverlay(root, "ios_control") != null) return;
        if (haptics) root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);

        setBlur(home, true);

        FrameLayout overlay = overlay(activity);
        overlay.setTag("ios_control");
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout sheet = new LinearLayout(activity);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(activity, 15), dp(activity, 15), dp(activity, 15), dp(activity, 18));
        sheet.setBackground(glass(activity, 32, 224));

        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(dp(activity, 340), -2, Gravity.TOP | Gravity.RIGHT);
        sp.setMargins(0, dp(activity, 38), dp(activity, 12), 0);
        overlay.addView(sheet, sp);

        LinearLayout top = new LinearLayout(activity);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout connectivity = new LinearLayout(activity);
        connectivity.setOrientation(LinearLayout.VERTICAL);
        connectivity.setPadding(dp(activity, 8),dp(activity, 8),dp(activity, 8),dp(activity, 8));
        connectivity.setBackground(glass(activity, 24, 196));

        LinearLayout row1 = new LinearLayout(activity);
        LinearLayout row2 = new LinearLayout(activity);

        TextView airplane = miniTile(activity,"✈","Airplane",false);
        TextView cellular = miniTile(activity,"▥","Cellular",true);
        TextView wifi = miniTile(activity,"⌁","Wi‑Fi",wifiActive(activity));
        TextView bluetooth = miniTile(activity,"ᛒ","Bluetooth",false);

        row1.addView(airplane,new LinearLayout.LayoutParams(0,dp(activity,58),1f));
        LinearLayout.LayoutParams cellLp=new LinearLayout.LayoutParams(0,dp(activity,58),1f);
        cellLp.setMargins(dp(activity,6),0,0,0);
        row1.addView(cellular,cellLp);

        row2.addView(wifi,new LinearLayout.LayoutParams(0,dp(activity,58),1f));
        LinearLayout.LayoutParams btLp=new LinearLayout.LayoutParams(0,dp(activity,58),1f);
        btLp.setMargins(dp(activity,6),0,0,0);
        row2.addView(bluetooth,btLp);

        connectivity.addView(row1,new LinearLayout.LayoutParams(-1,dp(activity,58)));
        LinearLayout.LayoutParams row2Lp=new LinearLayout.LayoutParams(-1,dp(activity,58));
        row2Lp.setMargins(0,dp(activity,6),0,0);
        connectivity.addView(row2,row2Lp);

        LinearLayout media = new LinearLayout(activity);
        media.setOrientation(LinearLayout.VERTICAL);
        media.setGravity(Gravity.CENTER);
        media.setBackground(glass(activity,24,196));
        TextView mediaTitle=text(activity,"Not Playing",14,Color.WHITE);
        mediaTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        mediaTitle.setGravity(Gravity.CENTER);
        media.addView(mediaTitle,new LinearLayout.LayoutParams(-1,dp(activity,54)));
        TextView mediaControls=text(activity,"◀     ▶",18,Color.argb(220,255,255,255));
        mediaControls.setGravity(Gravity.CENTER);
        media.addView(mediaControls,new LinearLayout.LayoutParams(-1,dp(activity,58)));

        top.addView(connectivity,new LinearLayout.LayoutParams(0,dp(activity,142),1f));
        LinearLayout.LayoutParams mediaLp=new LinearLayout.LayoutParams(0,dp(activity,142),1f);
        mediaLp.setMargins(dp(activity,10),0,0,0);
        top.addView(media,mediaLp);
        sheet.addView(top,new LinearLayout.LayoutParams(-1,dp(activity,142)));

        airplane.setOnClickListener(v -> safeStart(activity,new Intent(Settings.ACTION_WIRELESS_SETTINGS)));
        cellular.setOnClickListener(v -> openPanel(activity, Settings.Panel.ACTION_INTERNET_CONNECTIVITY, Settings.ACTION_DATA_ROAMING_SETTINGS));
        wifi.setOnClickListener(v -> openPanel(activity, Settings.Panel.ACTION_INTERNET_CONNECTIVITY, Settings.ACTION_WIFI_SETTINGS));
        bluetooth.setOnClickListener(v -> safeStart(activity, new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));

        // Brightness
        LinearLayout bright = sliderCard(activity, "☀  Brightness");
        SeekBar brightness = (SeekBar) bright.getChildAt(1);
        try {
            int current = Settings.System.getInt(activity.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
            brightness.setMax(255);
            brightness.setProgress(current);
        } catch (Exception ignored) {}
        brightness.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                if (Settings.System.canWrite(activity)) {
                    try {
                        Settings.System.putInt(activity.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, Math.max(5, progress));
                    } catch (Exception ignored) {}
                }
            }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (!Settings.System.canWrite(activity)) {
                    try {
                        Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(i);
                    } catch (Exception ignored) {}
                }
            }
        });
        sheet.addView(bright, cardParams(activity));

        // Volume
        LinearLayout volumeCard = sliderCard(activity, "🔊  Volume");
        SeekBar volume = (SeekBar) volumeCard.getChildAt(1);
        AudioManager am = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        volume.setMax(max);
        volume.setProgress(am.getStreamVolume(AudioManager.STREAM_MUSIC));
        volume.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) am.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
            }
        });
        sheet.addView(volumeCard, cardParams(activity));

        LinearLayout bottom = new LinearLayout(activity);
        bottom.setGravity(Gravity.CENTER);
        TextView settings = roundButton(activity, "⚙\nSettings");
        TextView notify = roundButton(activity, "◉\nNotifications");
        bottom.addView(settings, new LinearLayout.LayoutParams(0, dp(activity, 78), 1f));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, dp(activity, 78), 1f);
        np.setMargins(dp(activity, 10), 0, 0, 0);
        bottom.addView(notify, np);
        sheet.addView(bottom, new LinearLayout.LayoutParams(-1, dp(activity, 84)));

        settings.setOnClickListener(v -> runAndDismiss(root, overlay, sheet, home, settingsAction));
        notify.setOnClickListener(v -> {
            dismiss(root, overlay, sheet, home);
            showNotificationCenter(activity, root, home, haptics);
        });

        overlay.setOnClickListener(v -> {
            if (v == overlay) dismiss(root, overlay, sheet, home);
        });

        sheet.setTranslationY(-dp(activity, 50));
        sheet.setAlpha(0f);
        sheet.animate().translationY(0).alpha(1f).setDuration(250).setInterpolator(new DecelerateInterpolator()).start();
    }

    public static void showNotificationCenter(Activity activity, FrameLayout root, View home, boolean haptics) {
        if (findOverlay(root, "ios_notifications") != null) return;
        if (haptics) root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);

        setBlur(home, true);

        FrameLayout overlay = overlay(activity);
        overlay.setTag("ios_notifications");
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout sheet = new LinearLayout(activity);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(activity, 16), dp(activity, 24), dp(activity, 16), dp(activity, 18));
        sheet.setBackground(glass(activity, 0, 212));

        overlay.addView(sheet, new FrameLayout.LayoutParams(-1, -1));

        TextClock time = new TextClock(activity);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("HH:mm");
        time.setTextColor(Color.WHITE);
        time.setTextSize(54);
        time.setGravity(Gravity.CENTER);
        time.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        sheet.addView(time, new LinearLayout.LayoutParams(-1, dp(activity, 76)));

        TextView date = text(activity,
                new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(new Date()),
                16, Color.argb(210,255,255,255));
        date.setGravity(Gravity.CENTER);
        sheet.addView(date, new LinearLayout.LayoutParams(-1, dp(activity, 36)));

        TextView title = text(activity, "Notification Center", 16, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(dp(activity, 4), dp(activity, 18), 0, dp(activity, 8));
        sheet.addView(title, new LinearLayout.LayoutParams(-1, dp(activity, 54)));

        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        sheet.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        if (!IOSNotificationService.isEnabled(activity)) {
            LinearLayout card = notificationCard(activity, "Notification Access",
                    "Real notifications dikhane ke liye access enable karo.");
            list.addView(card, cardParams(activity));
            card.setOnClickListener(v -> safeStart(activity, new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
        } else {
            List<IOSNotificationService.Item> items = IOSNotificationService.snapshot();
            if (items.isEmpty()) {
                LinearLayout empty = notificationCard(activity, "No New Notifications", "You're all caught up.");
                list.addView(empty, cardParams(activity));
            } else {
                int limit = Math.min(items.size(), 18);
                for (int i = 0; i < limit; i++) {
                    IOSNotificationService.Item item = items.get(i);
                    LinearLayout card = notificationCard(activity,
                            item.appName + (item.title.isEmpty() ? "" : "  •  " + item.title),
                            item.text);
                    list.addView(card, cardParams(activity));
                    card.setOnClickListener(v -> {
                        try {
                            if (item.contentIntent != null) item.contentIntent.send();
                        } catch (Exception ignored) {}
                        dismiss(root, overlay, sheet, home);
                    });
                }
            }
        }

        TextView close = text(activity, "Close", 14, Color.WHITE);
        close.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        close.setGravity(Gravity.CENTER);
        close.setBackground(round(Color.argb(120,255,255,255), 18, activity));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(activity, 100), dp(activity, 38));
        cp.gravity = Gravity.CENTER_HORIZONTAL;
        cp.setMargins(0, dp(activity, 8), 0, 0);
        sheet.addView(close, cp);
        close.setOnClickListener(v -> dismiss(root, overlay, sheet, home));

        sheet.setTranslationY(-dp(activity, 90));
        sheet.setAlpha(0f);
        sheet.animate().translationY(0).alpha(1f).setDuration(280).setInterpolator(new DecelerateInterpolator()).start();
    }

    public static void showAppActions(Activity activity, FrameLayout root, View home,
                                      String appName, Drawable icon,
                                      Runnable open, Runnable addDock, Runnable customize,
                                      Runnable hide, Runnable info, Runnable uninstall,
                                      boolean haptics) {
        if (haptics) root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        setBlur(home, true);

        FrameLayout overlay = overlay(activity);
        overlay.setTag("ios_app_actions");
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(activity, 10), dp(activity, 10), dp(activity, 10), dp(activity, 10));
        card.setBackground(glass(activity, 26, 236));

        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(activity, 286), -2, Gravity.CENTER);
        overlay.addView(card, cp);

        LinearLayout head = new LinearLayout(activity);
        head.setGravity(Gravity.CENTER_VERTICAL);

        android.widget.ImageView iv = new android.widget.ImageView(activity);
        iv.setImageDrawable(icon);
        head.addView(iv, new LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 44)));

        TextView name = text(activity, appName, 16, Color.WHITE);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setPadding(dp(activity, 12), 0, 0, 0);
        head.addView(name, new LinearLayout.LayoutParams(0, dp(activity, 52), 1f));
        card.addView(head, new LinearLayout.LayoutParams(-1, dp(activity, 52)));

        addAction(activity, card, "Open", Color.WHITE, () -> runAndDismiss(root, overlay, card, home, open));
        addAction(activity, card, "Add to Dock", Color.WHITE, () -> runAndDismiss(root, overlay, card, home, addDock));
        addAction(activity, card, "Customize Icon", Color.WHITE, () -> runAndDismiss(root, overlay, card, home, customize));
        addAction(activity, card, "Hide from Home Screen", Color.WHITE, () -> runAndDismiss(root, overlay, card, home, hide));
        addAction(activity, card, "App Info", Color.WHITE, () -> runAndDismiss(root, overlay, card, home, info));
        addAction(activity, card, "Delete App", Color.rgb(255,69,58), () -> runAndDismiss(root, overlay, card, home, uninstall));

        overlay.setOnClickListener(v -> {
            if (v == overlay) dismiss(root, overlay, card, home);
        });

        card.setScaleX(.92f);
        card.setScaleY(.92f);
        card.setAlpha(0f);
        card.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(220)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private static void addAction(Activity a, LinearLayout parent, String label, int color, Runnable action) {
        TextView row = text(a, label, 15, color);
        row.setPadding(dp(a, 14), 0, dp(a, 14), 0);
        row.setBackground(round(Color.argb(34,255,255,255), 13, a));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(a, 43));
        rp.setMargins(0, dp(a, 4), 0, 0);
        parent.addView(row, rp);
        row.setOnClickListener(v -> action.run());
    }

    private static LinearLayout notificationCard(Activity a, String title, String body) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(a, 14), dp(a, 10), dp(a, 14), dp(a, 10));
        card.setBackground(glass(a, 20, 205));

        TextView t = text(a, title, 13, Color.WHITE);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(t, new LinearLayout.LayoutParams(-1, dp(a, 26)));

        TextView b = text(a, body == null ? "" : body, 12, Color.argb(205,255,255,255));
        card.addView(b, new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    private static LinearLayout sliderCard(Activity a, String label) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(a, 12), dp(a, 8), dp(a, 12), dp(a, 8));
        card.setBackground(glass(a, 20, 190));

        TextView title = text(a, label, 13, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(title, new LinearLayout.LayoutParams(-1, dp(a, 28)));

        SeekBar seek = new SeekBar(a);
        card.addView(seek, new LinearLayout.LayoutParams(-1, dp(a, 42)));
        return card;
    }

    private static TextView miniTile(Activity a, String symbol, String label, boolean active) {
        TextView v = text(a, symbol + "\n" + label, 11, Color.WHITE);
        v.setGravity(Gravity.CENTER);
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setBackground(round(active ? Color.rgb(48,130,246) : Color.rgb(75,77,84), 18, a));
        return v;
    }

    private static TextView tile(Activity a, String text, boolean active) {
        TextView v = text(a, text, 15, Color.WHITE);
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setGravity(Gravity.CENTER);
        v.setBackground(round(active ? Color.rgb(45,125,245) : Color.argb(95,255,255,255), 24, a));
        return v;
    }

    private static TextView roundButton(Activity a, String text) {
        TextView v = text(a, text, 12, Color.WHITE);
        v.setGravity(Gravity.CENTER);
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setBackground(round(Color.argb(70,255,255,255), 22, a));
        return v;
    }

    private static FrameLayout overlay(Activity a) {
        FrameLayout f = new FrameLayout(a);
        f.setBackgroundColor(Color.argb(112, 0, 0, 0));
        f.setClickable(true);
        return f;
    }

    private static GradientDrawable glass(Activity a, int radius, int alpha) {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[] {
                        Color.argb(alpha, 35, 38, 48),
                        Color.argb(Math.max(100, alpha - 55), 15, 17, 24)
                });
        g.setCornerRadius(dp(a, radius));
        g.setStroke(dp(a, 1), Color.argb(44,255,255,255));
        return g;
    }

    private static GradientDrawable round(int color, int radius, Activity a) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(a, radius));
        return g;
    }

    private static LinearLayout.LayoutParams cardParams(Activity a) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(a, 8), 0, 0);
        return lp;
    }

    private static TextView text(Activity a, String value, int sp, int color) {
        TextView t = new TextView(a);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private static int dp(Activity a, int n) {
        return Math.round(n * a.getResources().getDisplayMetrics().density);
    }

    private static boolean wifiActive(Activity a) {
        try {
            ConnectivityManager cm = (ConnectivityManager)a.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities nc = cm.getNetworkCapabilities(network);
            return nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } catch (Exception e) {
            return false;
        }
    }

    private static void openPanel(Activity a, String panel, String fallback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                a.startActivity(new Intent(panel));
                return;
            } catch (Exception ignored) {}
        }
        safeStart(a, new Intent(fallback));
    }

    private static void safeStart(Activity a, Intent i) {
        try { a.startActivity(i); } catch (Exception ignored) {}
    }

    private static View findOverlay(FrameLayout root, String tag) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View v = root.getChildAt(i);
            if (tag.equals(v.getTag())) return v;
        }
        return null;
    }

    private static void setBlur(View home, boolean on) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (on) {
                home.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(
                        18f, 18f, android.graphics.Shader.TileMode.CLAMP));
            } else {
                home.setRenderEffect(null);
            }
        } else {
            home.setAlpha(on ? .62f : 1f);
        }
    }

    private static void dismiss(FrameLayout root, FrameLayout overlay, View card, View home) {
        card.animate().alpha(0f).translationY(dp((Activity)root.getContext(), 30)).setDuration(180).withEndAction(() -> {
            try { root.removeView(overlay); } catch (Exception ignored) {}
            setBlur(home, false);
        }).start();
        overlay.animate().alpha(0f).setDuration(180).start();
    }

    private static void runAndDismiss(FrameLayout root, FrameLayout overlay, View card, View home, Runnable action) {
        dismiss(root, overlay, card, home);
        card.postDelayed(() -> { if (action != null) action.run(); }, 190);
    }

    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }
}
