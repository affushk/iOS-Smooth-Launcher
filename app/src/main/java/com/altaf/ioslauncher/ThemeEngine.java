package com.altaf.ioslauncher;
import android.content.Context;
import android.graphics.Color;
public final class ThemeEngine {
 private ThemeEngine(){}
 public static String name(Context c){return c.getSharedPreferences("launcher_prefs",Context.MODE_PRIVATE).getString("theme","AMOLED");}
 public static int background(Context c){String t=name(c);if("Midnight".equals(t))return Color.rgb(5,10,24);if("Bronze".equals(t))return Color.rgb(18,12,8);if("Graphite".equals(t))return Color.rgb(14,15,18);return Color.BLACK;}
 public static int surface(Context c){String t=name(c);if("Midnight".equals(t))return Color.rgb(15,24,48);if("Bronze".equals(t))return Color.rgb(42,29,20);if("Graphite".equals(t))return Color.rgb(31,33,38);return Color.rgb(16,17,20);}
 public static int accent(Context c){String t=name(c);if("Midnight".equals(t))return Color.rgb(92,145,255);if("Bronze".equals(t))return Color.rgb(202,157,104);if("Graphite".equals(t))return Color.rgb(190,196,208);return Color.rgb(235,235,240);}
 public static int glass(Context c,int a){int x=surface(c);return Color.argb(a,Color.red(x),Color.green(x),Color.blue(x));}
}