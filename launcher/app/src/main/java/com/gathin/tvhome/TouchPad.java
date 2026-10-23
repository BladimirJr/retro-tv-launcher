package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

/**
 * An on-screen arcade stick and OK button for trying Home on a phone (a theme's {@code "touch_controls": true}):
 * pushing the stick sends the D-pad keys a TV remote would, held keys repeat, and OK opens the focused tile.
 * The keys go through the activity's own key handling, as a remote's would, and an arrow nothing handles moves
 * focus the way the window would for a real key. Off by default and meant for testing; a TV has no touch screen to use it on.
 */
final class TouchPad extends View {

    private static final long REPEAT_DELAY_MS = 380L, REPEAT_MS = 140L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float density;
    private int heldKey;
    /** Where the stick's knob is pushed, -1 to 1 on each axis; the OK button's press state. */
    private float knobX, knobY;
    private boolean okDown;

    private final Runnable repeat = new Runnable() {
        @Override
        public void run() {
            if (heldKey == 0) return;
            send(heldKey);
            handler.postDelayed(this, REPEAT_MS);
        }
    };

    private TouchPad(Activity activity) {
        super(activity);
        this.activity = activity;
        density = activity.getResources().getDisplayMetrics().density;
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Adds the stick to the bottom right of {@code root}, above everything else. */
    static void attach(Activity activity, FrameLayout root) {
        TouchPad pad = new TouchPad(activity);
        float d = pad.density;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (250 * d), (int) (150 * d),
                Gravity.BOTTOM | Gravity.END);
        lp.setMargins(0, 0, (int) (16 * d), (int) (12 * d));
        root.addView(pad, lp);
    }

    private float stickX() { return getWidth() - 75 * density; }
    private float stickY() { return getHeight() / 2f; }
    private float okX() { return 45 * density; }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (Math.hypot(x - okX(), y - stickY()) < 40 * density) {
                    okDown = true;
                    send(KeyEvent.KEYCODE_DPAD_CENTER);
                } else {
                    push(x, y);
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!okDown) push(x, y);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                okDown = false;
                heldKey = 0;
                handler.removeCallbacks(repeat);
                knobX = knobY = 0f;
                invalidate();
                return true;
            default:
                return true;
        }
    }

    /** Points the knob at the touch, and sends (then repeats) the arrow it points to when that changes. */
    private void push(float x, float y) {
        float dx = x - stickX(), dy = y - stickY();
        float reach = 55 * density;
        float len = (float) Math.hypot(dx, dy);
        if (len < 14 * density) return;   // the dead centre
        float k = Math.min(1f, len / reach) / len;
        knobX = dx * k;
        knobY = dy * k;
        int key = Math.abs(dx) > Math.abs(dy)
                ? (dx > 0 ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT)
                : (dy > 0 ? KeyEvent.KEYCODE_DPAD_DOWN : KeyEvent.KEYCODE_DPAD_UP);
        if (key != heldKey) {
            heldKey = key;
            handler.removeCallbacks(repeat);
            send(key);
            handler.postDelayed(repeat, REPEAT_DELAY_MS);
        }
        invalidate();
    }

    /**
     * Presses and releases {@code keyCode} through the activity, as a remote's key arrives. An arrow nobody handles
     * moves focus to the next view that way, which the window itself does for real keys but not for dispatched ones.
     */
    private void send(int keyCode) {
        long now = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0);
        boolean handled = activity.dispatchKeyEvent(down);
        activity.dispatchKeyEvent(KeyEvent.changeAction(down, KeyEvent.ACTION_UP));
        View focused = activity.getCurrentFocus();
        if (handled || focused == null) return;
        View next;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT: next = focused.focusSearch(FOCUS_LEFT); break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: next = focused.focusSearch(FOCUS_RIGHT); break;
            case KeyEvent.KEYCODE_DPAD_UP: next = focused.focusSearch(FOCUS_UP); break;
            case KeyEvent.KEYCODE_DPAD_DOWN: next = focused.focusSearch(FOCUS_DOWN); break;
            default: return;
        }
        if (next != null) next.requestFocus();
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas c) {
        float sx = stickX(), sy = stickY(), d = density;
        // Base: a dark plate with a pink neon ring and four arrows.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x99100818);
        c.drawCircle(sx, sy, 62 * d, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3 * d);
        paint.setColor(0xCCFF2E9A);
        c.drawCircle(sx, sy, 62 * d, paint);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 4; i++) {
            c.save();
            c.rotate(i * 90f, sx, sy);
            arrow.reset();
            arrow.moveTo(sx, sy - 54 * d);
            arrow.lineTo(sx - 8 * d, sy - 44 * d);
            arrow.lineTo(sx + 8 * d, sy - 44 * d);
            arrow.close();
            paint.setColor(0xCCFFFFFF);
            c.drawPath(arrow, paint);
            c.restore();
        }
        // Knob: the ball on top of the stick, pushed towards the held direction.
        float kx = sx + knobX * 26 * d, ky = sy + knobY * 26 * d;
        paint.setColor(0x55000000);
        c.drawCircle(kx + 2 * d, ky + 3 * d, 24 * d, paint);
        paint.setColor(0xFFE0245E);
        c.drawCircle(kx, ky, 24 * d, paint);
        paint.setColor(0x66FFFFFF);
        c.drawCircle(kx - 7 * d, ky - 8 * d, 8 * d, paint);

        // OK button.
        float ox = okX();
        paint.setColor(okDown ? 0xFFFFE066 : 0xFFFFD84A);
        c.drawCircle(ox, sy + (okDown ? 2 * d : 0f), 32 * d, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xCC7A5A00);
        c.drawCircle(ox, sy + (okDown ? 2 * d : 0f), 32 * d, paint);
        paint.setStyle(Paint.Style.FILL);
        PixelFont.draw(c, "OK", ox - PixelFont.width("OK", 3 * d) / 2f, sy - 10.5f * d + (okDown ? 2 * d : 0f),
                3 * d, 0xFF3A2400, 0, paint);
    }
}
