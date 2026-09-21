package com.altaf.ioslauncher;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.content.ComponentName;
import java.util.Locale;

public final class IOSIconDrawable extends Drawable {
    private final Drawable inner;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect=new RectF();
    private final float cornerRatio;
    private final String identity;

    public IOSIconDrawable(Drawable inner,float cornerRatio){ this(inner,cornerRatio,""); }
    public IOSIconDrawable(Drawable inner,float cornerRatio,String identity){
        this.inner=inner; this.cornerRatio=Math.max(.22f,Math.min(.34f,cornerRatio));
        this.identity=identity==null?"":identity.toLowerCase(Locale.ROOT);
    }

    @Override public void draw(Canvas canvas){
        Rect b=getBounds(); float r=Math.min(b.width(),b.height())*cornerRatio;
        rect.set(b.left,b.top,b.right,b.bottom);
        int bg=background(); paint.setStyle(Paint.Style.FILL); paint.setColor(bg);
        canvas.drawRoundRect(rect,r,r,paint);
        if(drawSystemGlyph(canvas,b)) return;
        int inset=Math.round(Math.min(b.width(),b.height())*.11f);
        int save=canvas.save(); Path clip=new Path(); clip.addRoundRect(rect,r,r,Path.Direction.CW); canvas.clipPath(clip);
        inner.setBounds(b.left+inset,b.top+inset,b.right-inset,b.bottom-inset); inner.draw(canvas); canvas.restoreToCount(save);
    }

    private int background(){
        if(has("phone","dialer")) return Color.rgb(52,199,89);
        if(has("message","sms")) return Color.rgb(48,209,88);
        if(has("camera")) return Color.rgb(235,235,240);
        if(has("setting")) return Color.rgb(142,142,147);
        if(has("browser","chrome","internet")) return Color.rgb(0,122,255);
        if(has("gallery","photo")) return Color.WHITE;
        if(has("clock","alarm")) return Color.BLACK;
        if(has("calendar")) return Color.WHITE;
        return Color.rgb(242,242,247);
    }

    private boolean has(String... xs){ for(String x:xs) if(identity.contains(x)) return true; return false; }

    private boolean drawSystemGlyph(Canvas c,Rect b){
        float cx=b.exactCenterX(),cy=b.exactCenterY(),s=Math.min(b.width(),b.height());
        paint.setColor(Color.WHITE); paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        if(has("phone","dialer")){
            paint.setStrokeWidth(s*.105f);
            Path p=new Path(); p.moveTo(cx-s*.19f,cy-s*.22f); p.cubicTo(cx-s*.20f,cy+s*.02f,cx-s*.02f,cy+s*.20f,cx+s*.22f,cy+s*.20f); c.drawPath(p,paint);
            return true;
        }
        if(has("message","sms")){
            paint.setStyle(Paint.Style.FILL); RectF q=new RectF(cx-s*.27f,cy-s*.20f,cx+s*.27f,cy+s*.17f); c.drawRoundRect(q,s*.18f,s*.18f,paint);
            Path tail=new Path(); tail.moveTo(cx-s*.13f,cy+s*.13f); tail.lineTo(cx-s*.23f,cy+s*.27f); tail.lineTo(cx+s*.02f,cy+s*.15f); tail.close(); c.drawPath(tail,paint); return true;
        }
        if(has("setting")){
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(s*.09f); c.drawCircle(cx,cy,s*.20f,paint); c.drawCircle(cx,cy,s*.07f,paint);
            for(int i=0;i<8;i++){double a=i*Math.PI/4; c.drawLine(cx+(float)Math.cos(a)*s*.24f,cy+(float)Math.sin(a)*s*.24f,cx+(float)Math.cos(a)*s*.32f,cy+(float)Math.sin(a)*s*.32f,paint);} return true;
        }
        if(has("camera")){
            paint.setColor(Color.rgb(35,35,38)); paint.setStrokeWidth(s*.055f); RectF q=new RectF(cx-s*.27f,cy-s*.17f,cx+s*.27f,cy+s*.20f); c.drawRoundRect(q,s*.07f,s*.07f,paint); c.drawCircle(cx,cy+s*.01f,s*.105f,paint); return true;
        }
        return false;
    }

    @Override public void setAlpha(int a){paint.setAlpha(a);inner.setAlpha(a);}
    @Override public void setColorFilter(ColorFilter f){paint.setColorFilter(f);inner.setColorFilter(f);}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}