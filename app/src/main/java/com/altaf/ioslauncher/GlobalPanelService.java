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

public class GlobalPanelService extends Service {
    private WindowManager wm;
    private View edge;
    private float downX, downY;

    @Override public void onCreate() {
        super.onCreate();
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        edge = new View(this);
        edge.setBackgroundColor(Color.TRANSPARENT);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, dp(48),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP;
        edge.setOnTouchListener((v,e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { downX=e.getRawX(); downY=e.getRawY(); return true; }
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                if (e.getRawY()-downY > dp(30)) {
                    Intent i=new Intent(this,MainActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    i.putExtra("open_global_panel",true);
                    i.putExtra("panel_side",downX > getResources().getDisplayMetrics().widthPixels*.58f ? "control" : "notifications");
                    startActivity(i);
                }
                return true;
            }
            return true;
        });
        wm.addView(edge,lp);
    }
    private int dp(int n){ return Math.round(n*getResources().getDisplayMetrics().density); }
    @Override public void onDestroy(){ if(wm!=null && edge!=null) try{wm.removeView(edge);}catch(Exception ignored){} super.onDestroy(); }
    @Override public IBinder onBind(Intent intent){ return null; }
}
