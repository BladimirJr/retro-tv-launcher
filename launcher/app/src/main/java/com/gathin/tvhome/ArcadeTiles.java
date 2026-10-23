package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The arcade theme's tiles ({@code "scene": "arcade"}): a dark, nearly square cabinet card framed in neon in the
 * app's own colour, its face the app's name in the pixel font (AppAdapter.bindArcade), like picking a game on an
 * old cabinet's select screen.
 */
final class ArcadeTiles {

    private ArcadeTiles() {
    }

    /** The card itself: no fill, so the grid shows through, inside a two-tone neon frame. */
    static final class Frame extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final int color;
        private final float density;

        Frame(int color, float density) {
            this.color = color | 0xFF000000;
            this.density = density;
            fill.setColor(0x00000000);
            line.setStyle(Paint.Style.STROKE);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect b = getBounds();
            float r = AppAdapter.cornerRadius(b.width(), density);
            rect.set(b);
            line.setStyle(Paint.Style.STROKE);
            float outer = 3f * density;
            rect.inset(outer / 2f, outer / 2f);
            line.setStrokeWidth(outer);
            line.setColor(color);
            canvas.drawRoundRect(rect, r, r, line);
            rect.inset(outer, outer);
            line.setStrokeWidth(density);
            line.setColor((color & 0xFFFFFF) | 0x66000000);
            canvas.drawRoundRect(rect, r, r, line);
        }

        @Override
        public void setAlpha(int alpha) {
            fill.setAlpha(alpha);
            line.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            line.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
