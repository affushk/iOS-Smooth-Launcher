package com.altaf.ioslauncher;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.app.AlarmManager;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class GlobalPanelService extends Service {
    private WindowManager wm;
    private View edge;
    private float downX, downY;
    private View panel;
    private WindowManager.LayoutParams panelLp;

    @Override public void onCreate() {
        super.onCreate();
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        edge = new View(this);
        edge.setBackgroundColor(Color.TRANSPARENT);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, dp(14),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP;
        edge.setOnTouchListener((v,e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { downX=e.getRawX(); downY=e.getRawY(); return true; }
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                if (e.getRawY()-downY > dp(54) && Math.abs(e.getRawX()-downX) < dp(70)) {
                    showPanel(downX > getResources().getDisplayMetrics().widthPixels*.58f);
                }
                return true;
            }
            return true;
        });
        wm.addView(edge,lp);
    }
    private void showPanel(boolean control) {
        if (panel != null) return;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.argb(72,0,0,0));
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18),dp(18),dp(18),dp(18));
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(232,18,20,25),Color.argb(218,5,6,9)});
        bg.setCornerRadius(dp(28)); bg.setStroke(dp(1),Color.argb(48,255,255,255));
        card.setBackground(bg);
        TextView title=label(control?"Control Center":"Notification Center",22,true);
        card.addView(title,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView status=label(statusSummary(),13,false);
        status.setTextColor(Color.argb(210,255,255,255));
        card.addView(status,new LinearLayout.LayoutParams(-1,dp(38)));
        if(control){
            AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE);
            int mode=am==null?AudioManager.RINGER_MODE_NORMAL:am.getRingerMode();
            String sound=mode==AudioManager.RINGER_MODE_SILENT?"Silent":mode==AudioManager.RINGER_MODE_VIBRATE?"Vibrate":"Sound";
            TextView state=label(networkState()+"     •     "+sound,15,false);
            card.addView(state,new LinearLayout.LayoutParams(-1,dp(48)));
            TextView settings=label("Open system controls",16,true);
            settings.setGravity(Gravity.CENTER);
            settings.setBackground(round(Color.argb(45,255,255,255),18));
            settings.setOnClickListener(v->{ try{ Intent i=new Intent(Settings.ACTION_SETTINGS); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i);}catch(Exception ignored){} hidePanel();});
            LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,dp(58)); slp.setMargins(0,dp(12),0,0); card.addView(settings,slp);
        } else {
            java.util.List<IOSNotificationService.Item> items=IOSNotificationService.snapshot();
            if(items.isEmpty()) card.addView(label("No new notifications",15,false),new LinearLayout.LayoutParams(-1,dp(60)));
            int count=Math.min(items.size(),5);
            for(int i=0;i<count;i++){
                IOSNotificationService.Item n=items.get(i);
                TextView row=label("●  "+n.appName+"\n"+n.title+(n.text==null||n.text.isEmpty()?"":"  ·  "+n.text),14,false);
                row.setMaxLines(3); row.setPadding(dp(14),dp(10),dp(14),dp(10)); row.setBackground(round(Color.argb(42,255,255,255),18));
                LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(-1,-2); rlp.setMargins(0,dp(9),0,0); card.addView(row,rlp);
                final android.app.PendingIntent pi=n.contentIntent;
                row.setOnClickListener(v->{ if(pi!=null) try{pi.send();}catch(Exception ignored){} hidePanel();});
            }
        }
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
        cp.setMargins(dp(12),dp(34),dp(12),0); root.addView(card,cp);
        root.setOnTouchListener((v,e)->{ if(e.getActionMasked()==MotionEvent.ACTION_DOWN && e.getY()>card.getBottom()+dp(20)){hidePanel();return true;} return false;});
        panel=root;
        panelLp=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                PixelFormat.TRANSLUCENT);
        panelLp.gravity=Gravity.TOP; panelLp.dimAmount=.18f;
        if(Build.VERSION.SDK_INT>=31){ panelLp.flags|=WindowManager.LayoutParams.FLAG_BLUR_BEHIND; panelLp.setBlurBehindRadius(dp(18)); }
        try{wm.addView(panel,panelLp);}catch(Exception ex){panel=null;}
    }
    private String statusSummary(){
        StringBuilder s=new StringBuilder(networkState());
        try{
            AudioManager a=(AudioManager)getSystemService(AUDIO_SERVICE);
            int m=a.getRingerMode();
            if(m==AudioManager.RINGER_MODE_SILENT) s.append("  •  Silent");
            else if(m==AudioManager.RINGER_MODE_VIBRATE) s.append("  •  Vibrate");
        }catch(Exception ignored){}
        try{
            AlarmManager a=(AlarmManager)getSystemService(ALARM_SERVICE);
            AlarmManager.AlarmClockInfo info=a.getNextAlarmClock();
            if(info!=null) s.append("  •  Alarm ").append(new SimpleDateFormat("h:mm a",Locale.getDefault()).format(new Date(info.getTriggerTime())));
        }catch(Exception ignored){}
        int n=IOSNotificationService.snapshot().size();
        if(n>0) s.append("  •  ").append(n).append(" notif.");
        return s.toString();
    }
    private String networkState(){
        try{
            ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
            Network n=cm.getActiveNetwork(); NetworkCapabilities caps=cm.getNetworkCapabilities(n);
            if(caps==null) return "Offline";
            boolean wifi=caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            boolean cell=caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
            boolean net=caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            if(wifi) return net?"Wi-Fi connected":"Wi-Fi";
            if(cell) return net?"Mobile data":"Cellular";
            return net?"Internet connected":"Offline";
        }catch(Exception e){return "Network";}
    }
    private TextView label(String s,int size,boolean bold){ TextView v=new TextView(this); v.setText(s); v.setTextColor(Color.WHITE); v.setTextSize(size); v.setGravity(Gravity.CENTER_VERTICAL); if(bold)v.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD); return v; }
    private GradientDrawable round(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private void hidePanel(){ if(wm!=null&&panel!=null)try{wm.removeView(panel);}catch(Exception ignored){} panel=null; }
    private int dp(int n){ return Math.round(n*getResources().getDisplayMetrics().density); }
    @Override public void onDestroy(){ hidePanel(); if(wm!=null && edge!=null) try{wm.removeView(edge);}catch(Exception ignored){} super.onDestroy(); }
    @Override public IBinder onBind(Intent intent){ return null; }
}
