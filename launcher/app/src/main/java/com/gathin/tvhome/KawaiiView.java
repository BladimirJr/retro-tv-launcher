package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Random;

/**
 * Pastel "kawaii" backdrop for Home (a theme's {@code "scene": "kawaii"}): a lilac-to-pink sky with two rainbows
 * and a bank of fluffy clouds, a soft pink grid floor drifting towards the camera, little hearts floating up,
 * clouds sailing by and sparkles twinkling.
 *
 * The sky, rainbows and cloud bank are painted once at half resolution; each frame adds the floor's lines and a
 * few dozen hearts, clouds and sparkles, at about 30 fps between {@link #start()} and {@link #stop()}.
 */
public class KawaiiView extends View implements AnimatedScene {

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private static final long FRAME_MS = 33L;
    private static final int LANES = 22, HEARTS = 12, SPARKLES = 14;
    private static final int[] RAINBOW = {0xFFFF9AAE, 0xFFFFC59A, 0xFFFFEFA0, 0xFFB9EFC2, 0xFFA9D6FF, 0xFFD3B9FF};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Path heart = new Path();
    private final RectF skyRect = new RectF();
    /** Hearts: x, y (fractions of the screen), size, sway phase. Sparkles: x, y, phase. */
    private final float[][] hearts = new float[HEARTS][4];
    private final float[][] sparkles = new float[SPARKLES][3];
    private final Random random = new Random();
    private final Path cell = new Path();
    private final Paint scan = new Paint();
    private Bitmap sky;
    /**
     * The owner's own pictures from the theme folder, each optional (loadPicture): character.png sits on the clouds,
     * fairy.png flies about the sky, peek.png peeks up from the bottom edge, sticker.png floats by the left rainbow
     * and logo.png sits small at the top. The launcher bundles no character art of its own.
     */
    @Nullable private Bitmap character, fairy, peek, sticker, logo;
    private Shader floorShader;
    private float horizon;
    private boolean running;
    private long startedAt = SystemClock.uptimeMillis(), elapsedAtStop, lastFrame;

    public KawaiiView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        for (float[] h : hearts) resetHeart(h, random.nextFloat());
        Bitmap line = Bitmap.createBitmap(1, 4, Bitmap.Config.ARGB_8888);
        line.setPixel(0, 3, 0x14FFFFFF);
        scan.setShader(new android.graphics.BitmapShader(line, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        Random r = new Random(9);
        for (float[] s : sparkles) {
            s[0] = r.nextFloat();
            s[1] = r.nextFloat() * 0.55f;
            s[2] = r.nextFloat() * 6.28f;
        }
    }

    private void resetHeart(float[] h, float y) {
        h[0] = random.nextFloat();
        h[1] = y;
        h[2] = 0.6f + random.nextFloat() * 0.8f;
        h[3] = random.nextFloat() * 6.28f;
    }

    @Override
    public void start() {
        if (running || getVisibility() != VISIBLE) return;
        running = true;
        startedAt = SystemClock.uptimeMillis() - elapsedAtStop;
        lastFrame = SystemClock.uptimeMillis();
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
        recyclePictures();
        sky = null;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (w == 0 || h == 0) return;
        horizon = h * 0.60f;
        floorShader = new LinearGradient(0f, horizon, 0f, h, 0xFFF9C8DD, 0xFFF4A9C8, Shader.TileMode.CLAMP);
        if (sky != null) sky.recycle();
        sky = paintSky(w / 2, (int) horizon / 2 + 1);
        recyclePictures();
        character = loadPicture("character.png", (int) (h * 0.28f));
        fairy = loadPicture("fairy.png", (int) (h * 0.20f));
        peek = loadPicture("peek.png", (int) (h * 0.20f));
        sticker = loadPicture("sticker.png", (int) (h * 0.18f));
        logo = loadPicture("logo.png", (int) (h * 0.11f));
    }

    private static Bitmap paintSky(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0f, 0f, 0f, h, new int[]{0xFFC8B4EC, 0xFFE6BDE8, 0xFFFCD3E4},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0f, 0f, w, h, p);
        p.setShader(null);
        rainbow(c, p, w * 0.13f, h * 1.02f, h * 0.55f);
        rainbow(c, p, w * 0.80f, h * 1.02f, h * 0.48f);
        Random r = new Random(4);
        // Big fluffy clusters around the rainbows' feet and in the top corners.
        float[][] clusters = {{0.02f, 0.30f, 0.16f}, {0.30f, 0.78f, 0.13f}, {0.66f, 0.80f, 0.12f}, {0.99f, 0.66f, 0.13f},
                {0.86f, 0.70f, 0.14f}, {0.12f, 0.75f, 0.12f}};
        for (float[] k : clusters) {
            for (int i = 0; i < 6; i++) {
                cloudPuff(c, p, w * k[0] + (r.nextFloat() - 0.5f) * h * k[2] * 2.4f,
                        h * k[1] + (r.nextFloat() - 0.5f) * h * k[2], h * k[2] * (0.5f + 0.5f * r.nextFloat()));
            }
        }
        // A bank of clouds along the horizon, hiding the rainbows' feet.
        for (int i = 0; i < 26; i++) {
            float x = w * i / 25f + (r.nextFloat() - 0.5f) * w * 0.03f;
            float rad = h * (0.08f + 0.09f * r.nextFloat());
            cloudPuff(c, p, x, h - rad * 0.35f, rad);
        }
        return b;
    }

    private static void rainbow(Canvas c, Paint p, float cx, float cy, float radius) {
        p.setStyle(Paint.Style.STROKE);
        float band = radius * 0.08f;
        p.setStrokeWidth(band + 0.5f);
        for (int i = 0; i < RAINBOW.length; i++) {
            float rr = radius - i * band;
            p.setColor((RAINBOW[i] & 0xFFFFFF) | 0xD0000000);
            c.drawArc(cx - rr, cy - rr, cx + rr, cy + rr, 180f, 180f, false, p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    /** A soft white puff: a pale lilac shadow under a white circle. */
    private static void cloudPuff(Canvas c, Paint p, float x, float y, float rad) {
        p.setColor(0x40B9A2E0);
        c.drawCircle(x + rad * 0.08f, y + rad * 0.12f, rad, p);
        p.setColor(0xFFFFFBFD);
        c.drawCircle(x, y, rad, p);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        if (sky == null) onSizeChanged(w, h, 0, 0);
        long now = SystemClock.uptimeMillis();
        float t = (running ? now - startedAt : elapsedAtStop) / 1000f;
        float dt = running ? Math.min(0.1f, (now - lastFrame) / 1000f) : 0f;
        lastFrame = now;

        skyRect.set(0f, 0f, w, horizon);
        canvas.drawBitmap(sky, null, skyRect, bitmapPaint);

        // Two clouds sailing slowly across the upper sky.
        for (int i = 0; i < 2; i++) {
            float speed = 0.012f + 0.008f * i;
            float x = (((t * speed + i * 0.55f) % 1.3f) - 0.15f) * w, y = h * (0.10f + 0.12f * i);
            float rad = h * (0.035f + 0.01f * i);
            paint.setColor(0xE6FFFFFF);
            canvas.drawCircle(x, y, rad, paint);
            canvas.drawCircle(x + rad * 1.1f, y + rad * 0.2f, rad * 0.8f, paint);
            canvas.drawCircle(x - rad * 1.0f, y + rad * 0.25f, rad * 0.7f, paint);
            canvas.drawRect(x - rad * 1.0f, y + rad * 0.25f, x + rad * 1.1f, y + rad * 0.8f, paint);
        }

        // Floor: soft pink with white lines, rows drifting towards the camera.
        paint.setShader(floorShader);
        canvas.drawRect(0f, horizon, w, h, paint);
        paint.setShader(null);
        float depth = h - horizon, vx = w / 2f;
        float travel = t * 0.45f;
        int base = (int) travel;
        float phase = travel - base;
        // Every other floor cell a lighter pink, like tiles, riding along with the rows.
        paint.setColor(0x66FFFFFF);
        for (int i = 0; i < 13; i++) {
            float d0 = 1f + (i - phase) * 0.6f;
            if (d0 <= 0.05f) continue;
            float y0 = Math.min(horizon + depth / d0, h + depth), y1 = horizon + depth / (d0 + 0.6f);
            if (y1 >= h) continue;
            for (int j = (i + base) & 1; j < LANES; j += 2) {
                cell.reset();
                cell.moveTo(laneX(j, y0, vx, w, depth), y0);
                cell.lineTo(laneX(j + 1, y0, vx, w, depth), y0);
                cell.lineTo(laneX(j + 1, y1, vx, w, depth), y1);
                cell.lineTo(laneX(j, y1, vx, w, depth), y1);
                cell.close();
                canvas.drawPath(cell, paint);
            }
        }
        paint.setStrokeWidth(Math.max(1.5f, h / 400f));
        for (int i = 0; i <= LANES; i++) {
            paint.setColor(0xB3FFFFFF);
            canvas.drawLine(laneX(i, horizon, vx, w, depth), horizon, laneX(i, h, vx, w, depth), h, paint);
        }
        for (int i = 0; i < 14; i++) {
            float d = 1f + (i - phase) * 0.6f;
            if (d <= 0f) continue;
            float y = horizon + depth / d;
            if (y > h) continue;
            paint.setColor(((int) (90 + 110 * (y - horizon) / depth) << 24) | 0xFFFFFF);
            canvas.drawLine(0f, y, w, y, paint);
        }

        // Sparkles: four-pointed stars in white and butter yellow, twinkling.
        for (float[] s : sparkles) {
            float a = 0.5f + 0.5f * (float) Math.sin(t * 2f + s[2]);
            if (a < 0.2f) continue;
            float x = s[0] * w, y = s[1] * h + h * 0.03f, size = h * 0.016f * (0.6f + a);
            paint.setColor(((int) (a * 255) << 24) | (s[2] > 3.14f ? 0xFFF3A0 : 0xFFFFFF));
            star(canvas, x, y, size);
        }

        // Hearts floating up and fading near the top.
        for (float[] hh : hearts) {
            hh[1] -= dt * 0.03f * (0.7f + hh[2] * 0.4f);
            if (hh[1] < 0.05f) resetHeart(hh, 0.95f + random.nextFloat() * 0.1f);
            float x = hh[0] * w + (float) Math.sin(t * 1.2f + hh[3]) * h * 0.02f;
            float y = hh[1] * h, size = h * 0.018f * hh[2];
            float fade = Math.min(1f, (hh[1] - 0.05f) / 0.25f);
            paint.setColor(((int) (fade * 200) << 24) | 0xFF7FAE);
            drawHeart(canvas, x, y, size);
        }

        // The owner's pictures, each moving gently in its own way.
        float bob = (float) Math.sin(t * 1.4f) * h * 0.006f;
        if (sticker != null) {
            canvas.drawBitmap(sticker, w * 0.31f - sticker.getWidth() / 2f,
                    horizon * 0.72f - sticker.getHeight() / 2f + (float) Math.sin(t * 0.9f + 1f) * h * 0.012f, bitmapPaint);
        }
        if (character != null) {
            canvas.drawBitmap(character, w * 0.84f - character.getWidth() / 2f, horizon * 0.93f - character.getHeight() + bob,
                    bitmapPaint);
        }
        if (logo != null) {
            canvas.drawBitmap(logo, w * 0.66f - logo.getWidth() / 2f, h * 0.035f, bitmapPaint);
        }
        if (fairy != null) {
            // A slow figure of eight over the middle of the sky, tilting as she turns.
            float fx = w * (0.45f + 0.10f * (float) Math.sin(t * 0.25f)), fy = h * (0.27f + 0.05f * (float) Math.sin(t * 0.5f));
            canvas.save();
            canvas.rotate(8f * (float) Math.cos(t * 0.25f), fx, fy);
            canvas.drawBitmap(fairy, fx - fairy.getWidth() / 2f, fy - fairy.getHeight() / 2f, bitmapPaint);
            canvas.restore();
        }
        if (peek != null) {
            // Peeking up from the bottom edge, rising a little and sinking back every few seconds.
            float rise = 0.70f + 0.12f * (float) Math.sin(t * 0.8f);
            canvas.drawBitmap(peek, w * 0.06f, h - peek.getHeight() * rise, bitmapPaint);
        }

        // Soft screen lines over everything, like the reference's TV look.
        canvas.drawRect(0f, 0f, w, h, scan);

        if (running) postInvalidateDelayed(FRAME_MS);
    }

    /** Screen x of lane line {@code j} at height y on the floor. */
    private float laneX(int j, float y, float vx, int w, float depth) {
        float f = (j - LANES / 2f) / (LANES / 2f);
        float top = vx + f * w * 0.03f, bottom = vx + f * w * 1.5f;
        return top + (bottom - top) * (y - horizon) / depth;
    }

    private void recyclePictures() {
        for (Bitmap b : new Bitmap[]{character, fairy, peek, sticker, logo}) if (b != null) b.recycle();
        character = fairy = peek = sticker = logo = null;
    }

    /** {@code name} from the theme folder scaled to {@code targetH} pixels high, or null when the theme has none. */
    @Nullable
    private Bitmap loadPicture(String name, int targetH) {
        return loadScaled(getContext(), name, targetH);
    }

    /** A picture from the theme folder, decoded small and scaled to targetH high; null if missing or over 4 MB. */
    @Nullable
    static Bitmap loadScaled(Context context, String name, int targetH) {
        java.io.File dir = Config.dir(context);
        java.io.File file = dir == null ? null : new java.io.File(dir, name);
        if (file == null || !file.isFile() || file.length() > 4_000_000L || targetH <= 0) return null;
        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(file.getPath(), o);
        if (o.outHeight <= 0) return null;
        o.inJustDecodeBounds = false;
        o.inSampleSize = Math.max(1, Integer.highestOneBit(o.outHeight / targetH));
        Bitmap raw = android.graphics.BitmapFactory.decodeFile(file.getPath(), o);
        if (raw == null) return null;
        int tw = Math.max(1, raw.getWidth() * targetH / raw.getHeight());
        Bitmap scaled = Bitmap.createScaledBitmap(raw, tw, targetH, true);
        if (scaled != raw) raw.recycle();
        return scaled;
    }

    private void star(Canvas c, float x, float y, float s) {
        heart.reset();
        heart.moveTo(x, y - s);
        heart.quadTo(x, y, x + s, y);
        heart.quadTo(x, y, x, y + s);
        heart.quadTo(x, y, x - s, y);
        heart.quadTo(x, y, x, y - s);
        heart.close();
        c.drawPath(heart, paint);
    }

    private void drawHeart(Canvas c, float x, float y, float s) {
        heart.reset();
        heart.moveTo(x, y + s);
        heart.cubicTo(x - s * 1.6f, y - s * 0.2f, x - s * 0.8f, y - s * 1.3f, x, y - s * 0.45f);
        heart.cubicTo(x + s * 0.8f, y - s * 1.3f, x + s * 1.6f, y - s * 0.2f, x, y + s);
        heart.close();
        c.drawPath(heart, paint);
    }
}
