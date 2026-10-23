package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Random;

/**
 * Animated sci-fi backdrop for Home (a theme's {@code "scene": "bridge"}): a long lit bridge running out to a
 * misty horizon under a ring-world sky, the camera gliding slowly forward along it and swaying a little.
 *
 * The sky, the ring and the far cliffs never move relative to each other, so they are painted once into one
 * half-resolution bitmap and only shifted for parallax. The bridge is a handful of paths in perspective, redrawn
 * each frame at about 30 fps while Home is visible; {@link #stop()} halts it whenever Home is covered.
 */
public class BridgeView extends View implements AnimatedScene {

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private static final long FRAME_MS = 33L;
    /** World units: camera height over the deck, the deck's half width, the rails' height, gaps between seams and posts. */
    private static final float CAM_H = 0.55f, HALF_W = 1.05f, RAIL_H = 0.22f, SEAM = 1.6f, POST = 6.4f;
    /** Forward speed in world units per second, and the sway's period. */
    private static final float SPEED = 0.55f, SWAY_S = 26f;
    private static final float FAR = 220f;
    private static final int GLOW = 0xFF6FE6FF;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path();
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private Bitmap sky;
    private Shader deckShader, haze;
    private float horizon, focal;
    private boolean running;
    private long startedAt = SystemClock.uptimeMillis();
    /** Offset of the time-based motion, so stopping and starting carries on where it was. */
    private long elapsedAtStop;

    public BridgeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
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
        horizon = h * 0.56f;
        focal = h * 0.95f;
        deckShader = new LinearGradient(0f, horizon, 0f, h,
                new int[]{0xFF3A5A6E, 0xFF16222C, 0xFF0A1016}, new float[]{0f, 0.25f, 1f}, Shader.TileMode.CLAMP);
        haze = new LinearGradient(0f, horizon - h * 0.10f, 0f, horizon + h * 0.10f,
                new int[]{0x0098E8FF, 0x5598E8FF, 0x0098E8FF}, null, Shader.TileMode.CLAMP);
        if (sky != null) sky.recycle();
        sky = paintSky(w / 2 + w / 16, h / 2);
    }

    /** The sky, ring and far cliffs, at half resolution and 1/8 wider than the screen to leave room for the sway. */
    private Bitmap paintSky(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float hz = h * 0.56f;

        p.setShader(new LinearGradient(0f, 0f, 0f, h, new int[]{0xFF020409, 0xFF071827, 0xFF1D5268, 0xFF06111A, 0xFF020507},
                new float[]{0f, 0.32f, 0.56f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0f, 0f, w, h, p);

        // Stars in the upper sky, fainter towards the horizon's glow.
        Random r = new Random(7);
        p.setShader(null);
        for (int i = 0; i < 260; i++) {
            float y = (float) Math.pow(r.nextFloat(), 1.6) * hz * 0.85f;
            int a = (int) (40 + r.nextInt(150) * (1f - y / hz));
            p.setColor((a << 24) | 0xDDEEFF);
            c.drawCircle(r.nextFloat() * w, y, r.nextFloat() < 0.08f ? 1.1f : 0.6f, p);
        }

        // A distant moon, low on the left.
        float mx = w * 0.40f, my = h * 0.30f, mr = h * 0.035f;
        p.setShader(new RadialGradient(mx, my, mr * 4f, 0x3088C8FF, 0x0088C8FF, Shader.TileMode.CLAMP));
        c.drawCircle(mx, my, mr * 4f, p);
        p.setShader(new LinearGradient(mx - mr, my - mr, mx + mr, my + mr, 0xFFCFE6F2, 0xFF3C5866, Shader.TileMode.CLAMP));
        c.drawCircle(mx, my, mr, p);

        // The ring: a band that rises out of the horizon on both sides and arches over the sky, its sunlit face
        // bright, the inner edge in shadow. Only the part above the horizon is drawn.
        c.save();
        c.clipRect(0f, 0f, w, hz);
        RectF ring = new RectF(w * 0.12f, hz - h * 0.50f, w * 1.30f, hz + h * 0.50f);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(h * 0.050f);
        p.setColor(0x2290D8FF);
        c.drawOval(ring, p);
        p.setStrokeWidth(h * 0.018f);
        p.setShader(new LinearGradient(0f, hz - h * 0.50f, 0f, hz, 0xFFB8E4F0, 0x6680B8CC, Shader.TileMode.CLAMP));
        c.drawOval(ring, p);
        ring.inset(h * 0.014f, h * 0.014f);
        p.setShader(null);
        p.setStrokeWidth(h * 0.007f);
        p.setColor(0xCC0A1A24);
        c.drawOval(ring, p);
        p.setStyle(Paint.Style.FILL);
        c.restore();

        // The horizon's glow, where the sun has just set behind the ring's far side.
        p.setShader(new RadialGradient(w * 0.5f, hz, w * 0.55f, 0x6680DDF5, 0x0080DDF5, Shader.TileMode.CLAMP));
        c.drawRect(0f, 0f, w, h, p);

        // Cliffs on both sides of the abyss, darkest close to the bridge.
        p.setShader(null);
        p.setColor(0xFF04090D);
        drawCliffs(c, p, r, 0f, w * 0.40f, hz, h * 0.13f, true);
        drawCliffs(c, p, r, w * 0.60f, w, hz, h * 0.11f, false);
        p.setColor(0xFF0A1820);
        drawCliffs(c, p, r, w * 0.08f, w * 0.44f, hz, h * 0.05f, true);
        drawCliffs(c, p, r, w * 0.56f, w * 0.90f, hz, h * 0.045f, false);

        // Mist over the abyss below the horizon.
        p.setShader(new LinearGradient(0f, hz, 0f, h, 0x5530708A, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0f, hz, w, h, p);
        return b;
    }

    /** A jagged silhouette from x0 to x1 on the horizon, rising towards the outer edge when {@code left}. */
    private static void drawCliffs(Canvas c, Paint p, Random r, float x0, float x1, float hz, float peak, boolean left) {
        Path cliff = new Path();
        cliff.moveTo(x0, hz + 2f);
        int steps = 18;
        for (int i = 0; i <= steps; i++) {
            float f = (float) i / steps;
            float rise = left ? 1f - f : f;
            float y = hz - peak * (0.35f + 0.65f * rise) * (0.75f + 0.25f * r.nextFloat());
            cliff.lineTo(x0 + (x1 - x0) * f, y);
        }
        cliff.lineTo(x1, hz + 2f);
        cliff.close();
        c.drawPath(cliff, p);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        if (sky == null) onSizeChanged(w, h, 0, 0);   // released when detached
        float t = (running ? SystemClock.uptimeMillis() - startedAt : elapsedAtStop) / 1000f;
        float sway = (float) Math.sin(t * 2 * Math.PI / SWAY_S);

        // Sky: shifted against the sway for parallax.
        int skyW = sky.getWidth();
        float spare = skyW - w / 2f;
        int sx = (int) (spare / 2f * (1f + sway * 0.8f));
        src.set(sx, 0, sx + w / 2, sky.getHeight());
        dst.set(0f, 0f, w, h);
        canvas.drawBitmap(sky, src, dst, bitmapPaint);

        float vx = w / 2f + sway * w * 0.03f;
        float zNear = focal * CAM_H / (h - horizon);
        float travel = t * SPEED;

        // Deck, from just under the screen's edge out to the horizon.
        paint.setShader(deckShader);
        deck(vx, zNear * 0.9f, FAR, 0f, HALF_W);
        canvas.drawPath(path, paint);

        // Rails: low walls on both sides, lit from the horizon.
        paint.setShader(null);
        paint.setColor(0xFF0E1A22);
        for (int side = -1; side <= 1; side += 2) {
            path.reset();
            path.moveTo(sx(vx, side * HALF_W, zNear * 0.9f), sy(0f, zNear * 0.9f));
            path.lineTo(sx(vx, side * HALF_W, FAR), sy(0f, FAR));
            path.lineTo(sx(vx, side * HALF_W, FAR), sy(RAIL_H, FAR));
            path.lineTo(sx(vx, side * HALF_W, zNear * 0.9f), sy(RAIL_H, zNear * 0.9f));
            path.close();
            canvas.drawPath(path, paint);
        }

        // Seams across the deck, sliding towards the camera as it moves forward.
        paint.setStrokeWidth(Math.max(1f, h / 540f));
        float phase = travel % SEAM;
        for (int i = 0; i < 60; i++) {
            float z = zNear + i * SEAM - phase;
            if (z < zNear * 0.9f) continue;
            float fade = 1f - Math.min(1f, z / 70f);
            if (fade <= 0f) break;
            paint.setColor(((int) (fade * 70) << 24) | 0x7FD8F0);
            float y = sy(0f, z);
            canvas.drawLine(sx(vx, -HALF_W, z), y, sx(vx, HALF_W, z), y, paint);
        }

        // Glowing strips along the rails' tops and the deck's edges: a wide faint stroke under a thin bright one.
        for (int side = -1; side <= 1; side += 2) {
            for (int pass = 0; pass < 2; pass++) {
                paint.setStrokeWidth(pass == 0 ? h / 90f : Math.max(1.5f, h / 540f));
                paint.setColor(pass == 0 ? 0x3346D8FF : 0xE0A8F2FF);
                canvas.drawLine(sx(vx, side * HALF_W, zNear * 0.9f), sy(RAIL_H, zNear * 0.9f),
                        sx(vx, side * HALF_W, FAR), sy(RAIL_H, FAR), paint);
                paint.setColor(pass == 0 ? 0x2246D8FF : 0x8870D8F0);
                canvas.drawLine(sx(vx, side * HALF_W * 0.92f, zNear * 0.9f), sy(0f, zNear * 0.9f),
                        sx(vx, side * HALF_W * 0.92f, FAR), sy(0f, FAR), paint);
            }
        }

        // Light posts on the rails, passing by.
        float postPhase = travel % POST;
        for (int i = 0; i < 24; i++) {
            float z = zNear + i * POST - postPhase;
            if (z < zNear) continue;
            float fade = 1f - Math.min(1f, z / 120f);
            if (fade <= 0f) break;
            float top = RAIL_H + 0.55f;
            for (int side = -1; side <= 1; side += 2) {
                float x = sx(vx, side * HALF_W, z);
                paint.setStrokeWidth(Math.max(1f, focal * 0.05f / z));
                paint.setColor(((int) (fade * 200) << 24) | 0x0B151C);
                canvas.drawLine(x, sy(RAIL_H, z), x, sy(top, z), paint);
                float r = Math.max(1.2f, focal * 0.09f / z);
                paint.setColor(((int) (fade * 60) << 24) | (GLOW & 0xFFFFFF));
                canvas.drawCircle(x, sy(top, z), r * 2f, paint);
                paint.setColor(((int) (fade * 255) << 24) | 0xD8F8FF);
                canvas.drawCircle(x, sy(top, z), r, paint);
            }
        }

        // A pulse of light running out along the deck's centre line every few seconds.
        float pulseZ = zNear + ((t * 9f) % 90f);
        float pf = 1f - pulseZ / 90f;
        paint.setColor(((int) (pf * 150) << 24) | 0x9FEFFF);
        float pr = Math.max(1.5f, focal * 0.06f / pulseZ);
        canvas.drawOval(vx - pr * 6f, sy(0f, pulseZ) - pr, vx + pr * 6f, sy(0f, pulseZ) + pr, paint);

        // Haze over the far end, so the bridge dissolves into the horizon.
        paint.setShader(haze);
        canvas.drawRect(0f, horizon - h * 0.10f, w, horizon + h * 0.10f, paint);
        paint.setShader(null);

        if (running) postInvalidateDelayed(FRAME_MS);
    }

    /** A flat strip of the deck between depths z0 and z1, x from -halfW to +halfW, into {@link #path}. */
    private void deck(float vx, float z0, float z1, float y, float halfW) {
        path.reset();
        path.moveTo(sx(vx, -halfW, z0), sy(y, z0));
        path.lineTo(sx(vx, -halfW, z1), sy(y, z1));
        path.lineTo(sx(vx, halfW, z1), sy(y, z1));
        path.lineTo(sx(vx, halfW, z0), sy(y, z0));
        path.close();
    }

    /** Screen x of a world point x across the bridge at depth z. */
    private float sx(float vx, float x, float z) {
        return vx + focal * x / z;
    }

    /** Screen y of a point at height y over the deck at depth z. */
    private float sy(float y, float z) {
        return horizon + focal * (CAM_H - y) / z;
    }
}
