package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

/**
 * The "deploy" app opening (a theme's {@code "launch_transition": "deploy"}), like starting a match from a game
 * menu: the chosen tile's frame flares, a scan line shoots out from it across the screen, and a cold light floods
 * Home as the app opens over it with R.anim.deploy_enter. About a quarter of a second before the app is asked
 * to start, so it never feels slower than a plain open.
 */
final class DeployFlash extends View {

    private static final long MS = 260L;
    /** Share of the animation after which the app is started. */
    private static final float START_AT = 0.5f;
    private static boolean playing;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tile = new RectF();
    private final float radius, density;
    /** The theme's accent as RGB: the flare, a pale tint of it for the bright core, a near-black one for the flood. */
    private final int glow, core, flood;
    private float progress;

    private DeployFlash(Context context, View from, int accent) {
        super(context);
        glow = accent & 0xFFFFFF;
        core = Sky.lerpColor(accent | 0xFF000000, 0xFFFFFFFF, 0.78f) & 0xFFFFFF;
        flood = Sky.lerpColor(accent | 0xFF000000, 0xFF000000, 0.88f) & 0xFFFFFF;
        density = context.getResources().getDisplayMetrics().density;
        radius = 14f * density;
        int[] at = new int[2];
        from.getLocationInWindow(at);
        float sx = from.getScaleX(), sy = from.getScaleY();
        // getLocationInWindow already maps through the focus scale, so the corner is the scaled tile's.
        tile.set(at[0], at[1], at[0] + from.getWidth() * sx, at[1] + from.getHeight() * sy);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Plays the flare over {@code activity}'s Home from {@code from}, then runs {@code start}; once at a time. */
    static void play(Activity activity, View from, int accent, Runnable start) {
        if (playing) return;
        ViewGroup root = activity.findViewById(R.id.root);
        if (root == null || !from.isAttachedToWindow()) {
            start.run();
            return;
        }
        playing = true;
        DeployFlash flash = new DeployFlash(activity, from, accent);
        root.addView(flash, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f).setDuration(MS);
        anim.setInterpolator(new DecelerateInterpolator(1.4f));
        boolean[] started = {false};
        anim.addUpdateListener(a -> {
            flash.progress = (float) a.getAnimatedValue();
            flash.invalidate();
            if (!started[0] && flash.progress >= START_AT) {
                started[0] = true;
                start.run();
            }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!started[0]) start.run();
                // The light stays up while the window transition plays, then goes before Home is seen again.
                flash.postDelayed(() -> {
                    root.removeView(flash);
                    playing = false;
                }, 450);
            }
        });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float p = progress;
        int w = getWidth();
        float cy = tile.centerY();

        // The tile's frame flares up, then fades into the flood.
        float frame = p < 0.3f ? p / 0.3f : Math.max(0f, 1f - (p - 0.3f) / 0.7f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(6f * density);
        paint.setColor(((int) (frame * 90) << 24) | glow);
        canvas.drawRoundRect(tile, radius, radius, paint);
        paint.setStrokeWidth(2f * density);
        paint.setColor(((int) (frame * 255) << 24) | core);
        canvas.drawRoundRect(tile, radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);

        // The scan line: out from the tile's sides to the screen's edges in the first half.
        float grow = Math.min(1f, p / 0.45f);
        float left = tile.left - tile.left * grow, right = tile.right + (w - tile.right) * grow;
        float lineAlpha = p < 0.45f ? 1f : Math.max(0f, 1f - (p - 0.45f) / 0.4f);
        float thick = (2f + 10f * grow) * density;
        paint.setColor(((int) (lineAlpha * 70) << 24) | glow);
        canvas.drawRect(left, cy - thick * 3f, right, cy + thick * 3f, paint);
        paint.setColor(((int) (lineAlpha * 230) << 24) | core);
        canvas.drawRect(left, cy - thick / 4f, right, cy + thick / 4f, paint);

        // The flood: a cold light over everything in the second half.
        if (p > 0.35f) {
            float amount = Math.min(1f, (p - 0.35f) / 0.65f);
            canvas.drawColor(((int) (amount * 200) << 24) | flood);
            paint.setColor(((int) (amount * (1f - amount) * 4f * 60) << 24) | core);
            canvas.drawRect(0f, 0f, w, getHeight(), paint);
        }
    }
}
