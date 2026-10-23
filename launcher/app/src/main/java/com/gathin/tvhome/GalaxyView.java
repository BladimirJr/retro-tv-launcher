package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The galaxy screensaver's backdrop (a theme's {@code "screensaver": "galaxy"}): dark deep-space pictures drawn
 * in code, a spiral galaxy, a nebula or a deep field over a planet's edge, a new one at each {@link #next()},
 * crossfading over a few seconds. Between changes the whole view drifts and turns very slowly, which the GPU does
 * as a transform of the same picture: nothing is redrawn except during the crossfade.
 *
 * Pictures are painted at half resolution on a worker thread; two exist at most, during a crossfade.
 */
public class GalaxyView extends View {

    private static final long FADE_MS = 4000L;
    private static final long DRIFT_MS = 150_000L;

    private final ExecutorService painter = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF dst = new RectF();
    private final Random random = new Random();
    private Bitmap current, previous;
    private long fadeStart;
    private int kind = random.nextInt(3);
    private boolean drifting, outward = true;
    /** Detached for good: the painter is shut down and late pictures are dropped. */
    private boolean closed;

    public GalaxyView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    void start() {
        if (current == null) next();
        drifting = true;
        drift();
    }

    void stop() {
        drifting = false;
        animate().cancel();
    }

    /** Paints the next picture and crossfades to it. */
    void next() {
        if (closed) return;
        int w = Math.max(2, getResources().getDisplayMetrics().widthPixels / 2);
        int h = Math.max(2, getResources().getDisplayMetrics().heightPixels / 2);
        kind = (kind + 1 + random.nextInt(2)) % 3;
        int k = kind;
        long seed = random.nextLong();
        painter.execute(() -> {
            Bitmap b = paint(k, w, h, new Random(seed));
            main.post(() -> {
                if (closed) {
                    b.recycle();
                    return;
                }
                if (previous != null) previous.recycle();
                previous = current;
                current = b;
                fadeStart = SystemClock.uptimeMillis();
                invalidate();
            });
        });
    }

    /** A slow zoom and turn, back and forth, so stars never sit still long enough to burn in. */
    private void drift() {
        if (!drifting) return;
        float scale = outward ? 1.24f : 1.12f;
        float turn = outward ? 2.5f : -2.5f;
        outward = !outward;
        animate().scaleX(scale).scaleY(scale).rotation(turn).setDuration(DRIFT_MS)
                .setInterpolator(new LinearInterpolator()).withEndAction(this::drift).start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setScaleX(1.12f);
        setScaleY(1.12f);
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        closed = true;
        painter.shutdownNow();
        main.removeCallbacksAndMessages(null);
        if (current != null) current.recycle();
        if (previous != null) previous.recycle();
        current = previous = null;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.BLACK);
        if (current == null) return;
        dst.set(0f, 0f, getWidth(), getHeight());
        float f = Math.min(1f, (SystemClock.uptimeMillis() - fadeStart) / (float) FADE_MS);
        if (previous != null && f < 1f) {
            paint.setAlpha(255);
            canvas.drawBitmap(previous, null, dst, paint);
        }
        paint.setAlpha((int) (f * 255));
        canvas.drawBitmap(current, null, dst, paint);
        if (f < 1f) {
            postInvalidateDelayed(50);
        } else if (previous != null) {
            previous.recycle();
            previous = null;
        }
    }

    // ---- The pictures ----

    private static Bitmap paint(int kind, int w, int h, Random r) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0xFF010205);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        stars(c, p, r, w, h, 500);
        if (kind == 0) spiral(c, p, r, w, h);
        else if (kind == 1) nebula(c, p, r, w, h);
        else deepField(c, p, r, w, h);
        // A soft vignette keeps the edges dark under the clock wherever it drifts.
        p.setShader(new RadialGradient(w / 2f, h / 2f, w * 0.75f, new int[]{0x00000000, 0x00000000, 0xB0000000},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0f, 0f, w, h, p);
        return b;
    }

    private static void stars(Canvas c, Paint p, Random r, int w, int h, int n) {
        p.setShader(null);
        for (int i = 0; i < n; i++) {
            float m = r.nextFloat();
            int a = (int) (30 + 200 * m * m * m);
            int tint = r.nextFloat() < 0.2f ? 0xFFD8B0 : r.nextFloat() < 0.3f ? 0xB8D0FF : 0xFFFFFF;
            p.setColor((a << 24) | tint);
            c.drawCircle(r.nextFloat() * w, r.nextFloat() * h, m > 0.97f ? 1.3f : 0.5f + m * 0.4f, p);
        }
    }

    private static void glow(Canvas c, Paint p, float x, float y, float radius, int rgb, int alpha) {
        p.setShader(new RadialGradient(x, y, radius, (alpha << 24) | (rgb & 0xFFFFFF), rgb & 0xFFFFFF,
                Shader.TileMode.CLAMP));
        c.drawCircle(x, y, radius, p);
        p.setShader(null);
    }

    /** A tilted spiral galaxy: a warm core and two to four arms of blue stars with pink knots. */
    private static void spiral(Canvas c, Paint p, Random r, int w, int h) {
        float cx = w * (0.35f + 0.3f * r.nextFloat()), cy = h * (0.4f + 0.2f * r.nextFloat());
        float size = h * (0.38f + 0.12f * r.nextFloat());
        float tilt = 0.35f + 0.25f * r.nextFloat();
        double turn = r.nextDouble() * Math.PI;
        int arms = 2 + r.nextInt(3);
        glow(c, p, cx, cy, size * 1.3f, 0x1E3A6A, 70);
        for (int i = 0; i < 7000; i++) {
            int arm = r.nextInt(arms);
            float d = (float) Math.pow(r.nextFloat(), 0.8);
            double a = arm * 2 * Math.PI / arms + d * 4.2 + r.nextGaussian() * 0.32 * (1.1 - d);
            float rad = d * size;
            float x = (float) (Math.cos(a) * rad), y = (float) (Math.sin(a) * rad) * tilt;
            float px = cx + (float) (x * Math.cos(turn) - y * Math.sin(turn));
            float py = cy + (float) (x * Math.sin(turn) + y * Math.cos(turn));
            boolean knot = r.nextFloat() < 0.025f;
            int rgb = knot ? 0xFF7FB8 : d < 0.25f ? 0xFFE6C0 : 0xA8C8FF;
            p.setColor(((int) ((knot ? 150 : 70) * (1f - d * 0.6f)) << 24) | rgb);
            c.drawCircle(px, py, knot ? 1.2f : 0.7f, p);
        }
        glow(c, p, cx, cy, size * 0.35f, 0xFFD9A8, 150);
        glow(c, p, cx, cy, size * 0.08f, 0xFFF4E0, 230);
    }

    /** Clouds of gas along a sweeping curve, teal and violet with a warm edge, a few bright young stars in it. */
    private static void nebula(Canvas c, Paint p, Random r, int w, int h) {
        int[][] palettes = {{0x1FA2B8, 0x6A2CC8, 0xD0507A}, {0x2C58D8, 0x18A090, 0xE08A3C}, {0x8A2CC8, 0x2C3CD8, 0x30C0D0}};
        int[] pal = palettes[r.nextInt(palettes.length)];
        float x0 = -w * 0.1f, y0 = h * (0.2f + 0.6f * r.nextFloat());
        float x1 = w * 1.1f, y1 = h * (0.2f + 0.6f * r.nextFloat());
        float qx = w * 0.5f, qy = h * r.nextFloat();
        for (int i = 0; i < 90; i++) {
            float t = r.nextFloat();
            float x = (1 - t) * (1 - t) * x0 + 2 * (1 - t) * t * qx + t * t * x1;
            float y = (1 - t) * (1 - t) * y0 + 2 * (1 - t) * t * qy + t * t * y1;
            x += (float) r.nextGaussian() * w * 0.08f;
            y += (float) r.nextGaussian() * h * 0.10f;
            int rgb = pal[i % 3 == 2 && r.nextFloat() < 0.5f ? 2 : r.nextInt(2)];
            glow(c, p, x, y, h * (0.08f + 0.22f * r.nextFloat()), rgb, 18 + r.nextInt(26));
        }
        // Dark dust in front of the glow.
        for (int i = 0; i < 25; i++) {
            float t = r.nextFloat();
            float x = (1 - t) * (1 - t) * x0 + 2 * (1 - t) * t * qx + t * t * x1;
            float y = (1 - t) * (1 - t) * y0 + 2 * (1 - t) * t * qy + t * t * y1 + (float) r.nextGaussian() * h * 0.05f;
            glow(c, p, x, y, h * (0.04f + 0.08f * r.nextFloat()), 0x010205, 120);
        }
        for (int i = 0; i < 9; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h;
            glow(c, p, x, y, h * 0.03f, 0xCFE8FF, 120);
            p.setColor(0xF0FFFFFF);
            c.drawCircle(x, y, 1.4f, p);
            p.setColor(0x50FFFFFF);
            c.drawRect(x - h * 0.025f, y - 0.4f, x + h * 0.025f, y + 0.4f, p);
            c.drawRect(x - 0.4f, y - h * 0.025f, x + 0.4f, y + h * 0.025f, p);
        }
    }

    /** Faint distant galaxies over the dark edge of a planet with a thin blue atmosphere. */
    private static void deepField(Canvas c, Paint p, Random r, int w, int h) {
        stars(c, p, r, w, h, 400);
        for (int i = 0; i < 40; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h * 0.7f;
            float s = 2f + r.nextFloat() * 6f;
            c.save();
            c.rotate(r.nextFloat() * 180f, x, y);
            p.setShader(new RadialGradient(x, y, s, r.nextBoolean() ? 0x90FFE0C0 : 0x90C0D8FF, 0x00000000,
                    Shader.TileMode.CLAMP));
            c.scale(1f, 0.4f, x, y);
            c.drawCircle(x, y, s, p);
            c.restore();
        }
        p.setShader(null);
        float pr = w * 1.1f;
        float pcx = w * (0.3f + 0.4f * r.nextFloat()), pcy = h + pr * 0.86f;
        glow(c, p, pcx, pcy, pr * 1.06f, 0x3C8CFF, 60);
        p.setShader(new LinearGradient(0f, pcy - pr, 0f, pcy - pr * 0.9f, 0xFF0A1522, 0xFF020306, Shader.TileMode.CLAMP));
        c.drawCircle(pcx, pcy, pr, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.5f);
        p.setColor(0x9070B8FF);
        c.drawCircle(pcx, pcy, pr, p);
        p.setStyle(Paint.Style.FILL);
    }
}
