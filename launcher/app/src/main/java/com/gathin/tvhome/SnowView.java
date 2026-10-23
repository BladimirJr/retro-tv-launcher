package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Random;

/**
 * Winter night backdrop for Home (a theme's {@code "scene": "snow"}): a dark blue sky over snowy hills and a few
 * pines, with small snowflakes falling in three depths, each swaying gently on its way down.
 *
 * The landscape is painted once into a half-resolution bitmap; each frame is that bitmap plus about 160 dots,
 * at about 30 fps and only between {@link #start()} and {@link #stop()}.
 */
public class SnowView extends View implements AnimatedScene {

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private static final long FRAME_MS = 33L;
    private static final int FLAKES = 160;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final android.graphics.RectF dst = new android.graphics.RectF();
    /** Per flake: x and y as fractions of the screen, depth 0 (far) to 1 (near), sway phase. */
    private final float[][] flakes = new float[FLAKES][4];
    private final Random random = new Random();
    private Bitmap land;
    private boolean running;
    private long lastFrame;

    public SnowView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        for (float[] f : flakes) {
            f[0] = random.nextFloat();
            f[1] = random.nextFloat();
            f[2] = random.nextFloat();
            f[3] = random.nextFloat() * 6.28f;
        }
    }

    @Override
    public void start() {
        if (running || getVisibility() != VISIBLE) return;
        running = true;
        lastFrame = SystemClock.uptimeMillis();
        invalidate();
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        if (land != null) land.recycle();
        land = null;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (w == 0 || h == 0) return;
        if (land != null) land.recycle();
        land = paintLand(w / 2, h / 2);
    }

    /** Night sky, a soft moon glow, two rows of snowy hills and dark pines, at half resolution. */
    private static Bitmap paintLand(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0f, 0f, 0f, h, new int[]{0xFF02040B, 0xFF071226, 0xFF13263F},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0f, 0f, w, h, p);

        Random r = new Random(5);
        p.setShader(null);
        for (int i = 0; i < 120; i++) {
            p.setColor(((20 + r.nextInt(90)) << 24) | 0xE6F0FF);
            c.drawCircle(r.nextFloat() * w, r.nextFloat() * h * 0.55f, 0.5f, p);
        }
        float mx = w * 0.62f, my = h * 0.22f;
        p.setShader(new RadialGradient(mx, my, h * 0.30f, 0x40B8D4FF, 0x00B8D4FF, Shader.TileMode.CLAMP));
        c.drawCircle(mx, my, h * 0.30f, p);
        p.setShader(null);
        p.setColor(0xFFE8F0FA);
        c.drawCircle(mx, my, h * 0.035f, p);

        hills(c, p, r, w, h, h * 0.70f, h * 0.07f, 0xFF1C3352);
        pines(c, p, r, w, h * 0.70f, h * 0.10f, 0xFF0A1424, 7);
        hills(c, p, r, w, h, h * 0.82f, h * 0.06f, 0xFF2E4A6E);
        pines(c, p, r, w, h * 0.82f, h * 0.14f, 0xFF07101C, 5);
        // The snow cover nearest the camera, catching a little moonlight.
        p.setShader(new LinearGradient(0f, h * 0.88f, 0f, h, 0xFF4A6A90, 0xFF22364F, Shader.TileMode.CLAMP));
        Path ground = new Path();
        ground.moveTo(0f, h * 0.90f);
        ground.quadTo(w * 0.5f, h * 0.84f, w, h * 0.91f);
        ground.lineTo(w, h);
        ground.lineTo(0f, h);
        ground.close();
        c.drawPath(ground, p);
        p.setShader(null);
        return b;
    }

    private static void hills(Canvas c, Paint p, Random r, int w, int h, float base, float amp, int color) {
        Path hill = new Path();
        hill.moveTo(0f, base);
        int n = 6;
        for (int i = 0; i < n; i++) {
            float x0 = w * i / (float) n, x1 = w * (i + 1) / (float) n;
            hill.quadTo((x0 + x1) / 2f, base - amp * (0.4f + r.nextFloat()), x1, base + amp * (r.nextFloat() - 0.5f) * 0.3f);
        }
        hill.lineTo(w, h);
        hill.lineTo(0f, h);
        hill.close();
        p.setColor(color);
        c.drawPath(hill, p);
    }

    private static void pines(Canvas c, Paint p, Random r, int w, float base, float tall, int color, int count) {
        p.setColor(color);
        for (int i = 0; i < count; i++) {
            float x = w * (0.05f + 0.9f * r.nextFloat()), t = tall * (0.6f + 0.4f * r.nextFloat());
            Path tree = new Path();
            tree.moveTo(x, base - t);
            tree.lineTo(x + t * 0.28f, base);
            tree.lineTo(x - t * 0.28f, base);
            tree.close();
            c.drawPath(tree, p);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        if (land == null) onSizeChanged(w, h, 0, 0);
        dst.set(0f, 0f, w, h);
        canvas.drawBitmap(land, null, dst, bitmapPaint);

        long now = SystemClock.uptimeMillis();
        float dt = running ? Math.min(0.1f, (now - lastFrame) / 1000f) : 0f;
        lastFrame = now;
        float t = now / 1000f;
        float unit = h / 1080f;
        for (float[] f : flakes) {
            float depth = f[2];
            // Near flakes are bigger, brighter and fall faster: a cheap sense of depth.
            f[1] += dt * (0.035f + 0.09f * depth);
            f[0] += dt * 0.01f * (float) Math.sin(t * 0.7f + f[3]);
            if (f[1] > 1.02f) {
                f[1] = -0.02f;
                f[0] = random.nextFloat();
            }
            float x = f[0] * w + (float) Math.sin(t * (0.8f + depth) + f[3]) * 12f * unit * (0.5f + depth);
            float radius = (1.2f + 3.3f * depth * depth) * unit;
            paint.setColor(((int) (110 + 140 * depth) << 24) | 0xF4F8FF);
            canvas.drawCircle(x, f[1] * h, radius, paint);
        }

        if (running) postInvalidateDelayed(FRAME_MS);
    }
}
