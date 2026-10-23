package com.gathin.tvhome;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

/**
 * A retro "game loading" screen over the first Home after the TV starts, in the look of the theme's scene: a pixel
 * bar filling in steps under a blinking LOADING, then PRESS START blinks until any key (or a touch) is pressed, and
 * Home fades in behind a flash. Hearts and the theme's peek.png for the kawaii scene. Once per boot
 * (Settings.Global.BOOT_COUNT); keys pressed while it is still loading do nothing. The TV's own boot animation before it belongs to the system and cannot be changed without root.
 */
final class BootSplash extends View {

    private static final long FILL_MS = 2100L, FLASH_MS = 160L, FADE_MS = 350L;
    /** The key that closed the splash: its release must not reach Home either, or it could open a tile. */
    private static boolean swallowRelease;
    private static final int BLOCKS = 16;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pixels = new Paint();
    private final Path heart = new Path();
    private final RectF rect = new RectF();
    private final String scene;
    private final int accent, background;
    private final String loading;
    @Nullable private final Typeface font;
    @Nullable private Bitmap peek;
    private final long started = SystemClock.uptimeMillis();
    private boolean done;
    /** When PRESS START was answered, for the flash; 0 while waiting. */
    private long pressedAt;

    private BootSplash(Activity activity, String scene) {
        super(activity);
        this.scene = scene;
        boolean kawaii = "kawaii".equals(scene);
        accent = kawaii ? 0xFFE0457B : "arcade".equals(scene) ? 0xFFFF2E9A : "snow".equals(scene) ? 0xFFDDEBFF : 0xFF6FE6FF;
        background = kawaii ? 0xFFF9D3E6 : "snow".equals(scene) ? 0xFF050A16 : "bridge".equals(scene) ? 0xFF02070D : 0xFF07010D;
        loading = activity.getString(R.string.splash_loading);
        font = kawaii ? androidx.core.content.res.ResourcesCompat.getFont(activity, R.font.outfit_medium) : null;
        if (kawaii) peek = KawaiiView.loadScaled(activity, "peek.png", (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.22f));
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Shows the splash over {@code root} if this is the first Home since the TV started and the theme has a scene. */
    static void maybeShow(Activity activity, ViewGroup root, String scene) {
        if (scene.isEmpty()) return;
        int boot = Settings.Global.getInt(activity.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        SharedPreferences prefs = activity.getSharedPreferences("boot", Activity.MODE_PRIVATE);
        if (boot < 0 || prefs.getInt("splash_boot", -1) == boot) return;
        prefs.edit().putInt("splash_boot", boot).apply();
        root.addView(new BootSplash(activity, scene), new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** Lets the next Home build show the splash again this boot (Launcher settings' preview). */
    static void forgetShown(android.content.Context context) {
        context.getSharedPreferences("boot", Activity.MODE_PRIVATE).edit().remove("splash_boot").apply();
    }

    /**
     * Keys while the splash is up never reach Home: during loading they are ignored, at PRESS START any key closes it.
     * True when {@code event} was used here.
     */
    static boolean consumeKey(ViewGroup root, android.view.KeyEvent event) {
        boolean down = event.getAction() == android.view.KeyEvent.ACTION_DOWN;
        if (!down && swallowRelease) {
            swallowRelease = false;
            return true;
        }
        for (int i = root.getChildCount() - 1; i >= 0; i--) {
            View child = root.getChildAt(i);
            if (child instanceof BootSplash && !((BootSplash) child).done) {
                if (down && ((BootSplash) child).press()) swallowRelease = true;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent e) {
        if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP) press();
        return true;   // nothing under the splash is touchable while it is up
    }

    /** PRESS START answered: flash, then fade into Home. False while still loading. */
    private boolean press() {
        if (pressedAt != 0 || SystemClock.uptimeMillis() - started < FILL_MS) return false;
        pressedAt = SystemClock.uptimeMillis();
        invalidate();
        return true;
    }

    private void finish() {
        if (done) return;
        done = true;
        animate().alpha(0f).setDuration(FADE_MS).withEndAction(() -> {
            ViewGroup parent = (ViewGroup) getParent();
            if (parent != null) parent.removeView(this);
            if (peek != null) peek.recycle();
            peek = null;
        }).start();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        long t = SystemClock.uptimeMillis() - started;
        c.drawColor(background);
        boolean kawaii = "kawaii".equals(scene);

        // The bar fills in uneven steps, as old loaders did.
        float f = Math.min(1f, t / (float) FILL_MS);
        int filled = Math.min(BLOCKS, (int) (BLOCKS * (f < 0.6f ? f * 0.9f : f < 0.75f ? 0.54f + (f - 0.6f) * 0.3f : 0.585f + (f - 0.75f) * 1.66f) + 0.5f));
        if (f >= 1f) filled = BLOCKS;
        float barW = Math.min(w * 0.6f, h * 1.1f), barH = h * 0.06f;
        float bx = (w - barW) / 2f, by = h * 0.58f;
        float gap = barW * 0.012f, block = (barW - gap * (BLOCKS + 1)) / BLOCKS;

        if (kawaii) {
            if (peek != null) c.drawBitmap(peek, (w - peek.getWidth()) / 2f, by - peek.getHeight() - h * 0.12f, null);
            rect.set(bx - gap * 2, by - gap * 2, bx + barW + gap * 2, by + barH + gap * 2);
            paint.setColor(0xFFFFFFFF);
            c.drawRoundRect(rect, barH, barH, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(h * 0.006f);
            paint.setColor(0xFFF29AC0);
            c.drawRoundRect(rect, barH, barH, paint);
            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < BLOCKS; i++) {
                float cx = bx + gap + i * (block + gap) + block / 2f, cy = by + barH / 2f;
                drawHeart(c, cx, cy, Math.min(block, barH) * 0.42f, i < filled ? accent : 0x33E0457B);
            }
            paint.setTypeface(font);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(h * 0.05f);
            String text = t < FILL_MS ? loading + "...".substring(0, (int) (t / 300 % 4)) : (blink(t) ? "PRESS START" : "");
            paint.setColor(0xFFB0306A);
            c.drawText(text, w / 2f, by + barH + h * 0.11f, paint);
        } else {
            // Frame, then square blocks; a scanline overlay keeps the CRT look.
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2f, h / 270f));
            paint.setColor(accent);
            c.drawRect(bx - gap * 2, by - gap * 2, bx + barW + gap * 2, by + barH + gap * 2, paint);
            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < filled; i++) {
                float x = bx + gap + i * (block + gap);
                c.drawRect(x, by + gap, x + block, by + barH - gap, paint);
            }
            float px = Math.max(2f, h / 160f);
            String top = t < FILL_MS ? "LOADING" + "...".substring(0, (int) (t / 300 % 4)) : "";
            PixelFont.draw(c, top, (w - PixelFont.width("LOADING...", px)) / 2f, by - px * 16, px, 0xFFFFFFFF,
                    (accent & 0xFFFFFF) | 0x88000000, pixels);
            if (t >= FILL_MS && blink(t)) {
                float big = px * 1.4f;
                PixelFont.draw(c, "PRESS START", (w - PixelFont.width("PRESS START", big)) / 2f, by + barH + px * 8, big,
                        0xFFFFD84A, (accent & 0xFFFFFF) | 0xAA000000, pixels);
            }
            int pct = Math.round(100f * filled / BLOCKS);
            // The percentage just right of the bar's frame, centred on its height.
            PixelFont.draw(c, pct + "", bx + barW + gap * 2 + px * 4, by + barH / 2f - px * 3.5f, px,
                    (accent & 0xFFFFFF) | 0xCC000000, 0, pixels);
            paint.setShader(new LinearGradient(0, 0, 0, 3, 0x00000000, 0x30000000, Shader.TileMode.REPEAT));
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
        }

        if (pressedAt != 0) {
            // The answer to PRESS START: a white flash, then Home fades in.
            long since = SystemClock.uptimeMillis() - pressedAt;
            float flash = Math.max(0f, 1f - since / (float) FLASH_MS);
            c.drawColor(((int) (flash * 230) << 24) | 0xFFFFFF);
            if (since >= FLASH_MS) finish();
        }
        if (!done) postInvalidateOnAnimation();
    }

    /** PRESS START's blink: on for 0.7 s, off for 0.3 s, so it reads as there and waiting. */
    private static boolean blink(long t) {
        return (t - FILL_MS) % 1000 < 700;
    }

    private void drawHeart(Canvas c, float x, float y, float s, int color) {
        heart.reset();
        heart.moveTo(x, y + s);
        heart.cubicTo(x - s * 1.6f, y - s * 0.2f, x - s * 0.8f, y - s * 1.3f, x, y - s * 0.45f);
        heart.cubicTo(x + s * 0.8f, y - s * 1.3f, x + s * 1.6f, y - s * 0.2f, x, y + s);
        heart.close();
        paint.setColor(color);
        c.drawPath(heart, paint);
    }
}
