package com.altaf.ioslauncher;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.AudioManager;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class GlobalPanelService extends Service {
 private WindowManager wm; private View edge,panel; private float sx,sy;
 @Override public void onCreate(){super.onCreate(); if(!Settings.canDrawOverlays(this)){stopSelf();return;} wm=(WindowManager)getSystemService(WINDOW_SERVICE); installEdge();}
 private void installEdge(){
  edge=new View(this); edge.setBackgroundColor(Color.TRANSPARENT);
  WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,dp(10),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT); p.gravity=Gravity.TOP;
  edge.setOnTouchListener((v,e)->{if(e.getActionMasked()==0){sx=e.getRawX();sy=e.getRawY();return true;} if(e.getActionMasked()==1){if(e.getRawY()-sy>dp(46)&&Math.abs(e.getRawX()-sx)<dp(90)) showNotifications(); return true;} return true;}); wm.addView(edge,p);
 }
 private void showNotifications(){
  if(panel!=null)return;
  FrameLayout root=new FrameLayout(this); root.setBackgroundColor(Color.argb(46,0,0,0));
  LinearLayout sheet=new LinearLayout(this); sheet.setOrientation(LinearLayout.VERTICAL); sheet.setPadding(dp(18),dp(14),dp(18),dp(18));
  GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{Color.argb(218,14,16,20),Color.argb(205,3,4,6)}); bg.setCornerRadius(dp(30)); bg.setStroke(dp(1),Color.argb(45,255,255,255)); sheet.setBackground(bg);
  LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
  TextView title=txt("Notifications",22,true); top.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
  TextView clear=txt("Clear",14,true); clear.setGravity(Gravity.CENTER); clear.setPadding(dp(14),0,dp(14),0); clear.setBackground(round(Color.argb(36,255,255,255),16)); clear.setOnClickListener(v->{IOSNotificationService.clearAll();hide();});
  top.addView(clear,new LinearLayout.LayoutParams(dp(72),dp(38))); sheet.addView(top);
  TextView status=txt(status(),12,false); status.setTextColor(Color.LTGRAY); LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(34)); sp.setMargins(0,0,0,dp(8)); sheet.addView(status,sp);
  ScrollView scroll=new ScrollView(this); LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list,new ScrollView.LayoutParams(-1,-2));
  List<IOSNotificationService.Item> items=IOSNotificationService.snapshot();
  if(items.isEmpty()){TextView empty=txt("No new notifications",15,false); empty.setGravity(Gravity.CENTER); list.addView(empty,new LinearLayout.LayoutParams(-1,dp(110)));}
  for(IOSNotificationService.Item n:items){
   LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(14),dp(12),dp(14),dp(12)); row.setBackground(round(Color.argb(38,255,255,255),20));
   ImageView icon=new ImageView(this); try{icon.setImageDrawable(getPackageManager().getApplicationIcon(n.packageName));}catch(Exception x){icon.setImageResource(android.R.drawable.sym_def_app_icon);} row.addView(icon,new LinearLayout.LayoutParams(dp(40),dp(40)));
   LinearLayout words=new LinearLayout(this); words.setOrientation(LinearLayout.VERTICAL); words.setPadding(dp(12),0,0,0);
   TextView app=txt(n.appName,13,true); TextView body=txt(n.title+(n.text==null||n.text.isEmpty()?"":"\n"+n.text),14,false); body.setMaxLines(3); body.setEllipsize(android.text.TextUtils.TruncateAt.END); words.addView(app); words.addView(body); row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
   row.setOnClickListener(v->{if(n.contentIntent!=null)try{n.contentIntent.send();}catch(Exception ignored){}hide();});
   LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2); rp.setMargins(0,0,0,dp(10)); list.addView(row,rp);
  }
  sheet.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  TextView grab=txt("━━━━",18,true); grab.setGravity(Gravity.CENTER); grab.setTextColor(Color.GRAY); grab.setOnClickListener(v->hide()); sheet.addView(grab,new LinearLayout.LayoutParams(-1,dp(34)));
  FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(-1,(int)(getResources().getDisplayMetrics().heightPixels*.78f),Gravity.TOP); fp.setMargins(dp(10),dp(24),dp(10),0); root.addView(sheet,fp);
  root.setOnTouchListener(new View.OnTouchListener(){float y; public boolean onTouch(View v,MotionEvent e){if(e.getActionMasked()==0){y=e.getRawY();return true;}if(e.getActionMasked()==1&&e.getRawY()-y< -dp(55)){hide();return true;}return false;}});
  panel=root; WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_DIM_BEHIND,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP;p.dimAmount=.12f;if(Build.VERSION.SDK_INT>=31){p.flags|=WindowManager.LayoutParams.FLAG_BLUR_BEHIND;p.setBlurBehindRadius(dp(22));}try{wm.addView(panel,p);}catch(Exception x){panel=null;}
 }
 private String status(){StringBuilder s=new StringBuilder(net()); try{AudioManager a=(AudioManager)getSystemService(AUDIO_SERVICE);int m=a.getRingerMode();if(m==AudioManager.RINGER_MODE_SILENT)s.append("  •  Silent");else if(m==AudioManager.RINGER_MODE_VIBRATE)s.append("  •  Vibrate");}catch(Exception ignored){} try{AlarmManager a=(AlarmManager)getSystemService(ALARM_SERVICE);AlarmManager.AlarmClockInfo i=a.getNextAlarmClock();if(i!=null)s.append("  •  Alarm ").append(new SimpleDateFormat("h:mm a",Locale.getDefault()).format(new Date(i.getTriggerTime())));}catch(Exception ignored){} return s.toString();}
 private String net(){try{ConnectivityManager c=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);NetworkCapabilities n=c.getNetworkCapabilities(c.getActiveNetwork());if(n==null)return"Offline";if(n.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))return"Wi-Fi";if(n.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))return"Mobile data";return"Online";}catch(Exception e){return"Network";}}
 private TextView txt(String s,int z,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextColor(Color.WHITE);v.setTextSize(z);v.setGravity(Gravity.CENTER_VERTICAL);if(b)v.setTypeface(android.graphics.Typeface.DEFAULT,1);return v;}
 private GradientDrawable round(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;} private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private void hide(){if(panel!=null)try{wm.removeView(panel);}catch(Exception ignored){}panel=null;}
 @Override public void onDestroy(){hide();if(edge!=null)try{wm.removeView(edge);}catch(Exception ignored){}super.onDestroy();} @Override public IBinder onBind(Intent i){return null;}
}