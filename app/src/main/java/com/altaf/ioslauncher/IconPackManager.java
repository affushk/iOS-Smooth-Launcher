package com.altaf.ioslauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class IconPackManager {

    public static final class Pack {
        public final String packageName;
        public final String label;
        public final Drawable icon;

        public Pack(String packageName, String label, Drawable icon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }
    }

    private static final Map<String, Map<String,String>> CACHE = new HashMap<>();

    private IconPackManager() {}

    public static List<Pack> discover(Context context) {
        PackageManager pm = context.getPackageManager();
        Set<String> packages = new HashSet<>();
        List<ResolveInfo> candidates = new ArrayList<>();

        Intent launcher = new Intent(Intent.ACTION_MAIN);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        candidates.addAll(pm.queryIntentActivities(launcher, 0));

        String[] categories = new String[] {
                "com.novalauncher.THEME",
                "org.adw.launcher.THEMES",
                "com.anddoes.launcher.THEME",
                "com.teslacoilsw.launcher.THEME"
        };

        for (String category : categories) {
            Intent theme = new Intent(Intent.ACTION_MAIN);
            theme.addCategory(category);
            candidates.addAll(pm.queryIntentActivities(theme, 0));
        }

        List<Pack> out = new ArrayList<>();
        for (ResolveInfo r : candidates) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(context.getPackageName()) || !packages.add(pkg)) continue;
            if (!hasAppFilter(context, pkg)) continue;
            try {
                String label = r.loadLabel(pm).toString();
                Drawable icon = r.loadIcon(pm);
                out.add(new Pack(pkg, label, icon));
            } catch (Exception ignored) {}
        }

        Collections.sort(out, Comparator.comparing(p -> p.label.toLowerCase()));
        return out;
    }

    public static Drawable iconFor(Context context, String packPackage, ComponentName component, Drawable fallback) {
        if (packPackage == null || packPackage.trim().isEmpty()) return fallback;
        try {
            Map<String,String> map = CACHE.get(packPackage);
            if (map == null) {
                map = loadMap(context, packPackage);
                CACHE.put(packPackage, map);
            }

            String drawableName = null;
            String full = component.flattenToString();
            String shortName = component.flattenToShortString();
            drawableName = map.get(full);
            if (drawableName == null) drawableName = map.get(shortName);
            if (drawableName == null) drawableName = map.get(component.getPackageName());

            if (drawableName == null || drawableName.isEmpty()) return fallback;

            Context packContext = context.createPackageContext(packPackage, Context.CONTEXT_IGNORE_SECURITY);
            Resources res = packContext.getResources();

            int id = res.getIdentifier(drawableName, "drawable", packPackage);
            if (id == 0) id = res.getIdentifier(drawableName, "mipmap", packPackage);
            if (id == 0) return fallback;

            return res.getDrawable(id, packContext.getTheme());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static String labelForPackage(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) return "Default App Icons";
        try {
            PackageManager pm = context.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString();
        } catch (Exception ignored) {
            return "Selected Icon Pack";
        }
    }

    private static boolean hasAppFilter(Context context, String pkg) {
        try {
            Context packContext = context.createPackageContext(pkg, Context.CONTEXT_IGNORE_SECURITY);
            InputStream in = null;
            try {
                in = packContext.getAssets().open("appfilter.xml");
                if (in != null) {
                    in.close();
                    return true;
                }
            } catch (Exception ignored) {}

            int xmlId = packContext.getResources().getIdentifier("appfilter", "xml", pkg);
            return xmlId != 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Map<String,String> loadMap(Context context, String pkg) {
        Map<String,String> out = new HashMap<>();
        try {
            Context packContext = context.createPackageContext(pkg, Context.CONTEXT_IGNORE_SECURITY);

            try {
                InputStream in = packContext.getAssets().open("appfilter.xml");
                android.util.XmlPullAttributes attrs = null;
                org.xmlpull.v1.XmlPullParser parser = android.util.Xml.newPullParser();
                parser.setInput(in, "utf-8");
                parse(parser, out);
                in.close();
                if (!out.isEmpty()) return out;
            } catch (Exception ignored) {}

            int xmlId = packContext.getResources().getIdentifier("appfilter", "xml", pkg);
            if (xmlId != 0) {
                XmlResourceParser parser = packContext.getResources().getXml(xmlId);
                parse(parser, out);
                parser.close();
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static void parse(XmlPullParser parser, Map<String,String> out) throws Exception {
        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && "item".equalsIgnoreCase(parser.getName())) {
                String component = parser.getAttributeValue(null, "component");
                String drawable = parser.getAttributeValue(null, "drawable");
                if (component != null && drawable != null) {
                    String raw = component.trim();
                    if (raw.startsWith("ComponentInfo{") && raw.endsWith("}")) {
                        raw = raw.substring("ComponentInfo{".length(), raw.length() - 1);
                    }
                    ComponentName cn = ComponentName.unflattenFromString(raw);
                    if (cn != null) {
                        out.put(cn.flattenToString(), drawable);
                        out.put(cn.flattenToShortString(), drawable);
                        out.put(cn.getPackageName(), drawable);
                    } else {
                        out.put(raw, drawable);
                    }
                }
            }
            event = parser.next();
        }
    }
}
