package com.altaf.ioslauncher;
import android.content.Context;
import android.graphics.Color;
/** V29 master visual tokens. Inspired modes are original implementations, not vendor UI copies. */
public final class ThemeEngine {
 private ThemeEngine(){}
 public static final String ALTAF="Altaf AMOLED", GALAXY="Galaxy Style", STOCK="Stock Android", IOS="iOS Glass";
 public static String name(Context c){String n=c.getSharedPreferences("launcher_prefs",Context.MODE_PRIVATE).getString("theme",ALTAF);return normalize(n);}
 public static String normalize(String n){if("AMOLED".equals(n)||"Bronze".equals(n)||"Graphite".equals(n))return ALTAF;if("Midnight".equals(n))return STOCK;return n==null?ALTAF:n;}
 public static int background(Context c){switch(name(c)){case GALAXY:return Color.rgb(8,9,12);case STOCK:return Color.rgb(8,12,18);case IOS:return Color.rgb(5,6,9);default:return Color.BLACK;}}
 public static int surface(Context c){switch(name(c)){case GALAXY:return Color.rgb(28,29,34);case STOCK:return Color.rgb(27,31,40);case IOS:return Color.rgb(24,25,30);default:return Color.rgb(14,14,16);}}
 public static int surfaceAlt(Context c){switch(name(c)){case GALAXY:return Color.rgb(38,39,45);case STOCK:return Color.rgb(36,42,54);case IOS:return Color.rgb(35,36,43);default:return Color.rgb(25,25,28);}}
 public static int accent(Context c){switch(name(c)){case GALAXY:return Color.rgb(103,153,255);case STOCK:return Color.rgb(166,199,255);case IOS:return Color.rgb(86,156,255);default:return Color.rgb(207,165,105);}}
 public static int text(Context c){return Color.rgb(245,245,248);}
 public static int muted(Context c){return Color.rgb(174,176,184);}
 public static int radius(Context c){switch(name(c)){case GALAXY:return 26;case STOCK:return 22;case IOS:return 30;default:return 18;}}
 public static int glass(Context c,int a){int x=surface(c);return Color.argb(a,Color.red(x),Color.green(x),Color.blue(x));}
 public static float motion(Context c){switch(name(c)){case GALAXY:return .92f;case STOCK:return .82f;case IOS:return 1.05f;default:return .88f;}}
 public static void set(Context c,String n){c.getSharedPreferences("launcher_prefs",Context.MODE_PRIVATE).edit().putString("theme",normalize(n)).apply();}
}