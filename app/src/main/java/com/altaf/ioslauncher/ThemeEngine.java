package com.altaf.ioslauncher;
import android.content.Context;
import android.graphics.Color;

/** V30 visual system: four deliberately different component languages. */
public final class ThemeEngine {
 private ThemeEngine(){}
 public static final String ALTAF="Altaf AMOLED", GALAXY="Galaxy Style", STOCK="Stock Android", IOS="iOS Glass";
 public static String name(Context c){return normalize(c.getSharedPreferences("launcher_prefs",Context.MODE_PRIVATE).getString("theme",ALTAF));}
 public static boolean is(Context c,String n){return n.equals(name(c));}
 public static String normalize(String n){if(n==null)return ALTAF;if(ALTAF.equals(n)||GALAXY.equals(n)||STOCK.equals(n)||IOS.equals(n))return n;if("BLUE".equalsIgnoreCase(n)||"Midnight".equalsIgnoreCase(n)||"SYSTEM".equalsIgnoreCase(n))return STOCK;return ALTAF;}
 public static int background(Context c){switch(name(c)){case GALAXY:return Color.rgb(12,13,17);case STOCK:return Color.rgb(15,18,24);case IOS:return Color.rgb(9,12,18);default:return Color.BLACK;}}
 public static int surface(Context c){switch(name(c)){case GALAXY:return Color.rgb(30,31,37);case STOCK:return Color.rgb(31,35,44);case IOS:return Color.rgb(30,34,43);default:return Color.rgb(12,12,13);}}
 public static int surfaceAlt(Context c){switch(name(c)){case GALAXY:return Color.rgb(45,46,54);case STOCK:return Color.rgb(43,49,62);case IOS:return Color.rgb(45,50,62);default:return Color.rgb(24,22,20);}}
 public static int accent(Context c){switch(name(c)){case GALAXY:return Color.rgb(116,160,255);case STOCK:return Color.rgb(180,204,255);case IOS:return Color.rgb(82,168,255);default:return Color.rgb(214,169,102);}}
 public static int text(Context c){return Color.rgb(247,247,249);}
 public static int muted(Context c){return Color.rgb(168,171,181);}
 public static int radius(Context c){switch(name(c)){case GALAXY:return 28;case STOCK:return 18;case IOS:return 34;default:return 14;}}
 public static int dockRadius(Context c){switch(name(c)){case GALAXY:return 34;case STOCK:return 24;case IOS:return 40;default:return 16;}}
 public static int cardRadius(Context c){switch(name(c)){case GALAXY:return 30;case STOCK:return 20;case IOS:return 32;default:return 14;}}
 public static int glassAlpha(Context c){switch(name(c)){case GALAXY:return 232;case STOCK:return 245;case IOS:return 150;default:return 248;}}
 public static int panelAlpha(Context c){switch(name(c)){case IOS:return 185;case GALAXY:return 245;case STOCK:return 252;default:return 252;}}
 public static int glass(Context c,int a){int x=surface(c);return Color.argb(a,Color.red(x),Color.green(x),Color.blue(x));}
 public static float motion(Context c){switch(name(c)){case GALAXY:return .95f;case STOCK:return .80f;case IOS:return 1.08f;default:return .86f;}}
 public static float pressScale(Context c){switch(name(c)){case GALAXY:return .94f;case STOCK:return .97f;case IOS:return .92f;default:return .96f;}}
 public static int homeSpacing(Context c){switch(name(c)){case GALAXY:return 14;case STOCK:return 10;case IOS:return 16;default:return 8;}}
 public static String searchHint(Context c){switch(name(c)){case GALAXY:return "Search apps";case STOCK:return "Search your phone";case IOS:return "Search";default:return "Find";}}
 public static void set(Context c,String n){c.getSharedPreferences("launcher_prefs",Context.MODE_PRIVATE).edit().putString("theme",normalize(n)).apply();}
}