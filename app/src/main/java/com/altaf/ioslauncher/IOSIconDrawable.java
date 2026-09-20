package com.altaf.ioslauncher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

public final class IOSIconDrawable extends Drawable {
    private final Drawable inner;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float cornerRatio;
    private final int backgroundColor;

    public IOSIconDrawable(Drawable inner, float cornerRatio) {
        this.inner = inner;
        this.cornerRatio = Math.max(0.12f, Math.min(0.45f, cornerRatio));
        this.backgroundColor = dominantColor(inner);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        float radius = Math.min(b.width(), b.height()) * cornerRatio;

        rect.set(b.left, b.top, b.right, b.bottom);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(backgroundColor);
        canvas.drawRoundRect(rect, radius, radius, paint);

        int save = canvas.save();
        canvas.clipRoundRect(rect, radius, radius);

        int pad = Math.max(1, Math.round(Math.min(b.width(), b.height()) * 0.055f));
        inner.setBounds(b.left + pad, b.top + pad, b.right - pad, b.bottom - pad);
        inner.draw(canvas);

        canvas.restoreToCount(save);
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); inner.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter colorFilter) { paint.setColorFilter(colorFilter); inner.setColorFilter(colorFilter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    private static int dominantColor(Drawable drawable) {
        try {
            int size = 40;
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bitmap);
            drawable.setBounds(0, 0, size, size);
            drawable.draw(c);

            long r = 0, g = 0, b = 0, count = 0;
            for (int y = 0; y < size; y += 2) {
                for (int x = 0; x < size; x += 2) {
                    int px = bitmap.getPixel(x, y);
                    int a = Color.alpha(px);
                    if (a < 70) continue;
                    int rr = Color.red(px);
                    int gg = Color.green(px);
                    int bb = Color.blue(px);
                    if (rr > 245 && gg > 245 && bb > 245) continue;
                    r += rr; g += gg; b += bb; count++;
                }
            }

            if (count == 0) return Color.rgb(235, 235, 240);
            int rr = (int)(r / count);
            int gg = (int)(g / count);
            int bb = (int)(b / count);

            float factor = 0.80f;
            rr = Math.max(24, Math.min(235, (int)(rr * factor)));
            gg = Math.max(24, Math.min(235, (int)(gg * factor)));
            bb = Math.max(24, Math.min(235, (int)(bb * factor)));
            return Color.rgb(rr, gg, bb);
        } catch (Exception e) {
            return Color.rgb(55, 58, 68);
        }
    }
}
