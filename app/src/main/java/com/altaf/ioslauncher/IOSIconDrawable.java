package com.altaf.ioslauncher;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

public final class IOSIconDrawable extends Drawable {
    private final Drawable inner;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float cornerRatio;

    public IOSIconDrawable(Drawable inner, float cornerRatio) {
        this.inner = inner;
        this.cornerRatio = Math.max(0.22f, Math.min(0.34f, cornerRatio));
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        float radius = Math.min(b.width(), b.height()) * cornerRatio;
        rect.set(b.left, b.top, b.right, b.bottom);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(242, 242, 247));
        canvas.drawRoundRect(rect, radius, radius, paint);

        int save = canvas.save();
        Path clip = new Path();
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
        canvas.clipPath(clip);

        inner.setBounds(b.left, b.top, b.right, b.bottom);
        inner.draw(canvas);
        canvas.restoreToCount(save);
    }

    @Override public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        inner.setAlpha(alpha);
    }

    @Override public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        inner.setColorFilter(colorFilter);
    }

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
