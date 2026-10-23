package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Random;

/**
 * Retro arcade backdrop for Home (a theme's {@code "scene": "arcade"}): a neon grid floor rolling towards the
 * camera under a dark violet sky, twinkling sparkles, a pulsing magenta glow on the horizon, CRT scanlines and a
 * pixel-font "CREDIT 01" / blinking "INSERT COIN" at the bottom like an attract screen.
 *
 * The sky (gradient, faint stars, slanted bands) is painted once into a bitmap; the grid, sparkles and text are a
 * few hundred lines and squares per frame at about 30 fps, and only between {@link #start()} and {@link #stop()}.
 */
public class ArcadeView extends View implements AnimatedScene {

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private static final long FRAME_MS = 33L;
    /** Grid rows rolling by per second, and how many vertical lines fan out from the vanishing point. */
    private static final float ROWS_PER_S = 1.1f;
    private static final int LANES = 26;
    private static final int PINK = 0xFF2E9A, VIOLET = 0x9B3CFF;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pixels = new Paint();
    private final Paint scan = new Paint();
    private final float[][] sparkles = new float[16][3];
    private final android.graphics.Path cell = new android.graphics.Path();
    private final android.graphics.RectF skyRect = new android.graphics.RectF();
    private Bitmap sky;
    private Shader floorShader, glow;
    private float horizon;
    private boolean running;
    private long startedAt = SystemClock.uptimeMillis();
    private long elapsedAtStop;

    public ArcadeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        Random r = new Random(3);
        for (float[] s : sparkles) {
            s[0] = r.nextFloat();
            s[1] = r.nextFloat() * 0.5f;
            s[2] = r.nextFloat() * 6.28f;
        }
        // CRT scanlines: one dark row in three, tiled over the screen by a 1x3 shader.
        Bitmap line = Bitmap.createBitmap(1, 3, Bitmap.Config.ARGB_8888);
        line.setPixel(0, 2, 0x38000000);
        scan.setShader(new BitmapShader(line, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
    }

    @Override
    public void start() {
        if (running || getVisibility() != VISIBLE) return;
        running = true;
        startedAt = SystemClock.uptimeMillis() - elapsedAtStop;
        invalidate();
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        elapsedAtStop = SystemClock.uptimeMillis() - startedAt;
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        if (sky != null) sky.recycle();
        sky = null;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (w == 0 || h == 0) return;
        horizon = h * 0.58f;
        floorShader = new LinearGradient(0f, horizon, 0f, h, 0xFF1A0428, 0xFF050008, Shader.TileMode.CLAMP);
        glow = new RadialGradient(w / 2f, horizon, w * 0.42f, new int[]{0xAAFF2E9A, 0x44B0207A, 0x00000000},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
        if (sky != null) sky.recycle();
        sky = paintSky(w / 2, (int) horizon / 2 + 1);
    }

    /**
     * Sky above the horizon at half resolution: violet to black with stars and a faint nebula, a striped synthwave
     * sun going down behind neon-rimmed mountains, and palm silhouettes on both sides.
     */
    private static Bitmap paintSky(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0f, 0f, 0f, h, new int[]{0xFF06010D, 0xFF180424, 0xFF4A0C46},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0f, 0f, w, h, p);
        Random r = new Random(11);
        // A faint nebula, then stars.
        for (int i = 0; i < 6; i++) {
            float x = w * (0.1f + 0.8f * r.nextFloat()), y = h * 0.35f * r.nextFloat(), rad = h * (0.2f + 0.2f * r.nextFloat());
            int rgb = r.nextBoolean() ? 0x7A2CC8 : 0xC02C8A;
            p.setShader(new RadialGradient(x, y, rad, 0x30000000 | rgb, rgb, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, rad, p);
        }
        p.setShader(null);
        for (int i = 0; i < 160; i++) {
            p.setColor(((30 + r.nextInt(110)) << 24) | (r.nextFloat() < 0.3f ? 0xFF9AD0 : 0xFFFFFF));
            c.drawCircle(r.nextFloat() * w, r.nextFloat() * h * 0.75f, r.nextFloat() < 0.1f ? 0.9f : 0.5f, p);
        }

        // The sun: a glow, then a yellow-to-pink disc with horizontal gaps that widen towards the bottom.
        float sx = w / 2f, sy = h * 0.66f, sr = h * 0.42f;
        p.setShader(new RadialGradient(sx, sy, sr * 2f, 0x66FF3C8C, 0x00FF3C8C, Shader.TileMode.CLAMP));
        c.drawCircle(sx, sy, sr * 2f, p);
        Bitmap sun = Bitmap.createBitmap((int) (sr * 2) + 2, (int) (sr * 2) + 2, Bitmap.Config.ARGB_8888);
        Canvas sc = new Canvas(sun);
        Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
        sp.setShader(new LinearGradient(0f, 0f, 0f, sr * 2, new int[]{0xFFFFE66B, 0xFFFF9A3C, 0xFFFF2E8A},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        sc.drawCircle(sr + 1, sr + 1, sr, sp);
        sp.setShader(null);
        sp.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR));
        for (int i = 0; i < 7; i++) {
            float y = sr * (1.0f + 0.14f * i), gap = sr * (0.025f + 0.016f * i);
            sc.drawRect(0f, y, sun.getWidth(), y + gap, sp);
        }
        c.drawBitmap(sun, sx - sr - 1, sy - sr - 1, null);
        sun.recycle();

        // Mountains: a far purple range and a near dark one, both with a pink neon ridge.
        mountains(c, p, r, w, h, h * 0.70f, 0xFF2B0B40, 0xCCFF4FB8, 9);
        mountains(c, p, r, w, h, h * 0.40f, 0xFF12041C, 0xFFFF2E9A, 6);

        // Palms on both sides, the nearer ones taller.
        palm(c, p, w * 0.05f, h, h * 0.62f, 1);
        palm(c, p, w * 0.15f, h, h * 0.42f, 1);
        palm(c, p, w * 0.86f, h, h * 0.46f, -1);
        palm(c, p, w * 0.95f, h, h * 0.66f, -1);
        return b;
    }

    /** A jagged range from edge to edge whose peaks reach up to {@code peak} above the horizon line h. */
    private static void mountains(Canvas c, Paint p, Random r, int w, int h, float peak, int fill, int ridge, int n) {
        android.graphics.Path m = new android.graphics.Path();
        m.moveTo(0f, h);
        float[] xs = new float[n * 2 + 1], ys = new float[n * 2 + 1];
        for (int i = 0; i <= n * 2; i++) {
            xs[i] = w * i / (float) (n * 2);
            // Higher at the sides, so the sun shows through a valley in the middle.
            float side = Math.abs(xs[i] / w - 0.5f) * 2f;
            ys[i] = i % 2 == 0 ? h - peak * 0.15f * r.nextFloat()
                    : h - peak * (0.35f + 0.65f * side) * (0.7f + 0.3f * r.nextFloat());
            m.lineTo(xs[i], ys[i]);
        }
        m.lineTo(w, h);
        m.close();
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(fill);
        c.drawPath(m, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.2f);
        p.setColor(ridge);
        for (int i = 0; i < xs.length - 1; i++) c.drawLine(xs[i], ys[i], xs[i + 1], ys[i + 1], p);
        // Faint wireframe lines down the faces.
        p.setColor((ridge & 0xFFFFFF) | 0x40000000);
        for (int i = 1; i < xs.length - 1; i += 2) c.drawLine(xs[i], ys[i], xs[i] + (r.nextFloat() - 0.5f) * w * 0.04f, h, p);
        p.setStyle(Paint.Style.FILL);
    }

    /** A palm silhouette rooted at x on the horizon line h, {@code tall} high, leaning towards {@code lean} (1 right). */
    private static void palm(Canvas c, Paint p, float x, int h, float tall, int lean) {
        float topX = x + lean * tall * 0.18f, topY = h - tall;
        android.graphics.Path trunk = new android.graphics.Path();
        trunk.moveTo(x - tall * 0.03f, h);
        trunk.quadTo(x + lean * tall * 0.02f, h - tall * 0.5f, topX - tall * 0.012f, topY);
        trunk.lineTo(topX + tall * 0.012f, topY);
        trunk.quadTo(x + lean * tall * 0.06f, h - tall * 0.5f, x + tall * 0.03f, h);
        trunk.close();
        p.setShader(null);
        p.setColor(0xFF07010C);
        c.drawPath(trunk, p);
        // Fronds: drooping leaves fanned around the crown.
        float[] angles = {-170, -140, -110, -70, -40, -10, 20, 160};
        for (float deg : angles) {
            double a = Math.toRadians(deg);
            float len = tall * 0.42f;
            float ex = topX + (float) Math.cos(a) * len, ey = topY + (float) Math.sin(a) * len * 0.55f + len * 0.35f;
            float cx = topX + (float) Math.cos(a) * len * 0.55f, cy = topY + (float) Math.sin(a) * len * 0.6f - len * 0.12f;
            android.graphics.Path leaf = new android.graphics.Path();
            leaf.moveTo(topX, topY);
            leaf.quadTo(cx, cy - len * 0.06f, ex, ey);
            leaf.quadTo(cx, cy + len * 0.08f, topX, topY + len * 0.03f);
            leaf.close();
            p.setColor(0xFF07010C);
            c.drawPath(leaf, p);
        }
        // A thin pink rim on the trunk where the sun's glow would catch it.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.8f);
        p.setColor(0x88FF4FB8);
        c.drawPath(trunk, p);
        p.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        if (sky == null) onSizeChanged(w, h, 0, 0);
        float t = (running ? SystemClock.uptimeMillis() - startedAt : elapsedAtStop) / 1000f;

        skyRect.set(0f, 0f, w, horizon);
        canvas.drawBitmap(sky, null, skyRect, null);

        // Floor.
        paint.setShader(floorShader);
        canvas.drawRect(0f, horizon, w, h, paint);
        paint.setShader(null);

        // Glow on the horizon, breathing slowly.
        float breathe = 0.8f + 0.2f * (float) Math.sin(t * 1.3);
        paint.setShader(glow);
        paint.setAlpha((int) (255 * breathe));
        canvas.drawRect(0f, horizon - h * 0.40f, w, horizon + h * 0.25f, paint);
        paint.setShader(null);
        paint.setAlpha(255);

        float vx = w / 2f;
        float depth = h - horizon;
        float travel = t * ROWS_PER_S;
        int base = (int) travel;
        float phase = travel - base;

        // Floor cells lighting up: one in four squares glows a dull fuchsia and fades again, riding with the grid.
        for (int i = 0; i < 17; i++) {
            float d0 = 1f + (i - phase) * 0.6f;
            if (d0 <= 0.05f) continue;
            float y0 = Math.min(horizon + depth / d0, h + depth), y1 = horizon + depth / (d0 + 0.6f);
            if (y1 >= h) continue;
            int rowId = i + base;
            for (int j = 0; j < LANES; j++) {
                int hash = rowId * 73856093 ^ j * 19349663;
                hash ^= hash >>> 13;
                hash *= 0x5BD1E995;
                hash ^= hash >>> 15;
                if ((hash & 7) > 1) continue;
                float a = 0.5f + 0.5f * (float) Math.sin(t * 1.8f + ((hash >>> 8) & 0xFF) / 40f);
                if (a < 0.2f) continue;
                cell.reset();
                cell.moveTo(laneX(j, y0, vx, w, depth), y0);
                cell.lineTo(laneX(j + 1, y0, vx, w, depth), y0);
                cell.lineTo(laneX(j + 1, y1, vx, w, depth), y1);
                cell.lineTo(laneX(j, y1, vx, w, depth), y1);
                cell.close();
                paint.setColor(((int) (a * a * 170) << 24) | 0xC0247E);
                canvas.drawPath(cell, paint);
            }
        }
        // Vertical lanes fanning out from the vanishing point; the outer ones run off the screen's sides.
        for (int i = 0; i <= LANES; i++) {
            float f = (i - LANES / 2f) / (LANES / 2f);
            float bottomX = vx + f * w * 1.6f;
            line(canvas, vx + f * w * 0.02f, horizon, bottomX, h, 1f - Math.abs(f) * 0.4f, h);
        }
        // Horizontal rows rolling towards the camera: evenly spaced in depth, so bunched up near the horizon.
        for (int i = 0; i < 18; i++) {
            float d = 1f + (i - phase) * 0.6f;   // distance in units of the screen's bottom edge
            if (d <= 0f) continue;
            float y = horizon + depth / d;
            if (y > h) continue;
            float near = (y - horizon) / depth;
            line(canvas, 0f, y, w, y, 0.25f + 0.75f * near, h);
        }
        // The horizon line itself.
        line(canvas, 0f, horizon, w, horizon, 1f, h);

        // Sparkles: four-pointed pink stars that twinkle.
        for (float[] s : sparkles) {
            float a = 0.5f + 0.5f * (float) Math.sin(t * 2.2f + s[2]);
            if (a < 0.15f) continue;
            float x = s[0] * w, y = s[1] * h + h * 0.04f, size = h * 0.012f * (0.6f + a);
            paint.setColor(((int) (a * 255) << 24) | 0xFF7AC4);
            canvas.drawRect(x - size, y - size * 0.12f, x + size, y + size * 0.12f, paint);
            canvas.drawRect(x - size * 0.12f, y - size, x + size * 0.12f, y + size, paint);
            paint.setColor(((int) (a * 90) << 24) | 0xFF7AC4);
            canvas.drawCircle(x, y, size * 0.5f, paint);
        }

        // Attract-screen text at the bottom.
        float px = Math.max(2f, h / 200f);
        drawText(canvas, "CREDIT 01", w * 0.04f, h - px * 13, px, 0xFFFFFFFF);
        if ((int) (t * 1.6f) % 2 == 0) {
            String coin = "INSERT COIN";
            drawText(canvas, coin, w * 0.96f - coin.length() * 6 * px, h - px * 13, px, 0xFFFFD84A);
        }

        canvas.drawRect(0f, 0f, w, h, scan);

        if (running) postInvalidateDelayed(FRAME_MS);
    }

    /** Screen x of lane line {@code j} at height y on the floor (the same fan as the drawn lanes). */
    private float laneX(int j, float y, float vx, int w, float depth) {
        float f = (j - LANES / 2f) / (LANES / 2f);
        float top = vx + f * w * 0.02f, bottom = vx + f * w * 1.6f;
        return top + (bottom - top) * (y - horizon) / depth;
    }

    /** A neon line: a wide faint violet stroke under a thin bright pink one, at {@code strength} 0 to 1. */
    private void line(Canvas c, float x0, float y0, float x1, float y1, float strength, int h) {
        paint.setStrokeWidth(h / 140f);
        paint.setColor(((int) (strength * 60) << 24) | VIOLET);
        c.drawLine(x0, y0, x1, y1, paint);
        paint.setStrokeWidth(Math.max(1.5f, h / 540f));
        paint.setColor(((int) (strength * 235) << 24) | PINK);
        c.drawLine(x0, y0, x1, y1, paint);
    }

    private void drawText(Canvas c, String text, float x, float y, float px, int color) {
        PixelFont.draw(c, text, x, y, px, color, 0x88FF2E9A, pixels);
    }
}
