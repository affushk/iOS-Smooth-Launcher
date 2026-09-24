package com.altaf.ioslauncher;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class IOSNotificationService extends NotificationListenerService {

    public static final class Item {
        public final String key;
        public final String packageName;
        public final String appName;
        public final String title;
        public final String text;
        public final long time;
        public final PendingIntent contentIntent;

        Item(String key, String packageName, String appName, String title, String text,
             long time, PendingIntent contentIntent) {
            this.key = key;
            this.packageName = packageName;
            this.appName = appName;
            this.title = title;
            this.text = text;
            this.time = time;
            this.contentIntent = contentIntent;
        }
    }

    private static final List<Item> ITEMS = new ArrayList<>();
    private static volatile IOSNotificationService INSTANCE;

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        INSTANCE = this;
        rebuild();
    }

    @Override
    public void onDestroy() {
        if (INSTANCE == this) INSTANCE = null;
        super.onDestroy();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        upsert(sbn);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        synchronized (ITEMS) {
            ITEMS.removeIf(i -> i.key.equals(sbn.getKey()));
        }
    }

    private void rebuild() {
        try {
            StatusBarNotification[] active = getActiveNotifications();
            synchronized (ITEMS) {
                ITEMS.clear();
                if (active != null) {
                    for (StatusBarNotification sbn : active) addInternal(sbn);
                    sortInternal();
                }
            }
        } catch (Exception ignored) {}
    }

    private void upsert(StatusBarNotification sbn) {
        synchronized (ITEMS) {
            ITEMS.removeIf(i -> i.key.equals(sbn.getKey()));
            addInternal(sbn);
            sortInternal();
        }
    }

    private void addInternal(StatusBarNotification sbn) {
        try {
            Notification n = sbn.getNotification();
            CharSequence titleCs = n.extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence textCs = n.extras.getCharSequence(Notification.EXTRA_TEXT);

            String appName;
            try {
                appName = getPackageManager().getApplicationLabel(
                        getPackageManager().getApplicationInfo(sbn.getPackageName(), 0)
                ).toString();
            } catch (Exception e) {
                appName = sbn.getPackageName();
            }

            ITEMS.add(new Item(
                    sbn.getKey(),
                    sbn.getPackageName(),
                    appName,
                    titleCs == null ? "" : titleCs.toString(),
                    textCs == null ? "" : textCs.toString(),
                    sbn.getPostTime(),
                    n.contentIntent
            ));
        } catch (Exception ignored) {}
    }

    private void sortInternal() {
        Collections.sort(ITEMS, (a, b) -> Long.compare(b.time, a.time));
    }

    public static List<Item> snapshot() {
        synchronized (ITEMS) {
            return new ArrayList<>(ITEMS);
        }
    }

    public static boolean clearOne(String key) {
        IOSNotificationService service = INSTANCE;
        if (service == null || key == null) return false;
        try {
            service.cancelNotification(key);
            synchronized (ITEMS) { ITEMS.removeIf(i -> i.key.equals(key)); }
            return true;
        } catch (Exception e) { return false; }
    }

    public static boolean clearAll() {
        IOSNotificationService service = INSTANCE;
        if (service == null) return false;
        try {
            service.cancelAllNotifications();
            synchronized (ITEMS) {
                ITEMS.clear();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isEnabled(android.content.Context context) {
        try {
            String flat = android.provider.Settings.Secure.getString(
                    context.getContentResolver(), "enabled_notification_listeners");
            if (flat == null) return false;
            String me = new ComponentName(context, IOSNotificationService.class).flattenToString();
            return flat.contains(me);
        } catch (Exception e) {
            return false;
        }
    }
}
