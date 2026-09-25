package com.altaf.ioslauncher;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;\nimport android.app.WallpaperManager;\nimport android.graphics.BitmapFactory;\nimport java.io.InputStream;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private LinearLayout body; private int accent=Color.rgb(83,145,255); private int pickMode=0; private Uri wallpaperUri;
    @Override public void onCreate(Bundle b){super.onCreate(b); showHome();}
    private TextView t(String s,int z,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextColor(Color.WHITE);v.setTextSize(z);v.setGravity(Gravity.CENTER_VERTICAL);v.setPadding(dp(16),0,dp(16),0);if(bold)v.setTypeface(null,1);return v;}
    private GradientDrawable bg(int color,int r){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(r));return g;}
    private void base(String title){body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),dp(24),dp(18),dp(16));body.setBackgroundColor(Color.rgb(4,18,47));setContentView(body);TextView h=t(title,28,true);body.addView(h,new LinearLayout.LayoutParams(-1,dp(64)));}
    private TextView card(String s){TextView v=t(s,17,true);v.setBackground(bg(Color.rgb(16,42,84),22));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(72));p.setMargins(0,0,0,dp(12));body.addView(v,p);return v;}
    private void showHome(){base("Altaf Theme Studio"); EditText search=new EditText(this);search.setHint("Search themes");search.setHintTextColor(Color.LTGRAY);search.setTextColor(Color.WHITE);search.setSingleLine();search.setBackground(bg(Color.rgb(25,55,102),28));body.addView(search,new LinearLayout.LayoutParams(-1,dp(58)));
      Space s=new Space(this);body.addView(s,new LinearLayout.LayoutParams(1,dp(18)));
      TextView imp=card("＋  Import theme / font");imp.setOnClickListener(v->{pickMode=1;pick("*/*");});
      TextView icons=card("◉  Icon Style");icons.setOnClickListener(v->showEditor("Icon Style"));
      TextView create=card("✦  Create New Theme");create.setOnClickListener(v->showEditor("New Theme"));
      TextView my=card("▣  My Themes");my.setOnClickListener(v->Toast.makeText(this,"Saved themes will appear here",Toast.LENGTH_SHORT).show());
      TextView info=t("Target: vivo Y75 • Android 13 • Themes V20.5.6.0\nOffline creator • Preview • Save • Import/Export",13,false);info.setTextColor(Color.rgb(180,199,230));body.addView(info,new LinearLayout.LayoutParams(-1,dp(74)));
      Space fill=new Space(this);body.addView(fill,new LinearLayout.LayoutParams(1,0,1));
      LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);nav.setBackground(bg(Color.rgb(10,31,68),30));String[] ns={"⌂ Home","✎ Editor","◈ Customize","⚙ Settings"};for(String n:ns){TextView x=t(n,12,false);x.setGravity(Gravity.CENTER);nav.addView(x,new LinearLayout.LayoutParams(0,dp(58),1));if(n.contains("Editor"))x.setOnClickListener(v->showEditor("Theme Editor"));}body.addView(nav,new LinearLayout.LayoutParams(-1,dp(58)));
    }
    private void showEditor(String title){base(title);EditText name=new EditText(this);name.setHint("Theme name e.g. Altaf_Y75_Test_v1");name.setTextColor(Color.WHITE);name.setHintTextColor(Color.GRAY);name.setBackground(bg(Color.rgb(16,42,84),18));body.addView(name,new LinearLayout.LayoutParams(-1,dp(58)));
      String[] opts={"Wallpaper / Lock wallpaper","Accent & background colors","Icon image / shape / size","Font resource & label size","Grid / Dock / Search style","Cards / Transparency / Radius","Animation speed","Preview Home / Dialer / Messages / Settings"};
      for(String o:opts){TextView v=card(o+"   ›");v.setOnClickListener(x->Toast.makeText(this,o,Toast.LENGTH_SHORT).show());}
      TextView save=card("✓  Save Theme");save.setBackground(bg(accent,22));save.setOnClickListener(v->{getSharedPreferences("themes",0).edit().putString("last_name",name.getText().toString()).putLong("saved",System.currentTimeMillis()).apply();Toast.makeText(this,"Theme saved locally",Toast.LENGTH_SHORT).show();});
      TextView back=card("‹  Back to Home");back.setOnClickListener(v->showHome());
    }
    private void showColors(){final String[] n={"Blue","AMOLED Gold","Green","Purple","Red"};final int[] a={Color.rgb(83,145,255),Color.rgb(214,169,102),Color.rgb(55,200,120),Color.rgb(155,110,255),Color.rgb(245,90,90)};new android.app.AlertDialog.Builder(this).setTitle("Accent color").setItems(n,(d,w)->{accent=a[w];getSharedPreferences("themes",0).edit().putInt("accent",accent).apply();Toast.makeText(this,n[w]+" selected",Toast.LENGTH_SHORT).show();}).show();}\n    private void showSliders(String title,int min,int max){SeekBar s=new SeekBar(this);s.setMax(max-min);s.setProgress((max-min)/2);new android.app.AlertDialog.Builder(this).setTitle(title).setView(s).setPositiveButton("Save",(d,w)->getSharedPreferences("themes",0).edit().putInt(title,s.getProgress()+min).apply()).setNegativeButton("Cancel",null).show();}\n    private void showPreview(){new android.app.AlertDialog.Builder(this).setTitle("Live Theme Preview").setMessage("Home • Dialer • Messages • Settings • Notification panel\\n\\nAccent, wallpaper, icon and layout settings saved in this theme.").setPositiveButton("OK",null).show();}\n    private void pick(String type){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(type);startActivityForResult(i,22);}
    @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(r==22&&c==RESULT_OK&&d!=null){Uri u=d.getData();if(u!=null){try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}if(pickMode==2){wallpaperUri=u;getSharedPreferences("themes",0).edit().putString("wallpaper",u.toString()).apply();try(InputStream in=getContentResolver().openInputStream(u)){WallpaperManager.getInstance(this).setBitmap(BitmapFactory.decodeStream(in));Toast.makeText(this,"Wallpaper applied",Toast.LENGTH_LONG).show();}catch(Exception e){Toast.makeText(this,"Wallpaper saved; Android blocked apply",Toast.LENGTH_LONG).show();}}else{getSharedPreferences("themes",0).edit().putString(pickMode==3?"font":"imported",u.toString()).apply();Toast.makeText(this,"Imported: "+u.getLastPathSegment(),Toast.LENGTH_LONG).show();}}}}
    @Override public void onBackPressed(){showHome();}
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
}