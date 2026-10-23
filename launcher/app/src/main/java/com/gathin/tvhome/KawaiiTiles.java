package com.gathin.tvhome;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The kawaii theme's tiles ({@code "scene": "kawaii"}): creamy pastel cards, each in one of five decorated frames
 * (a red bow, a border of hearts, corner stars, music notes, dots), and the app's name in round bold letters with a
 * white outline.
 */
final class KawaiiTiles {

    private KawaiiTiles() {
    }

    /** The app's colour softened towards white, so every frame stays pastel. */
    static int pastel(int color) {
        return Sky.lerpColor(color | 0xFF000000, 0xFFFFFFFF, 0.45f);
    }

    /** The app's colour pulled towards a deep berry, dark enough to read on the white card. */
    static int ink(int color) {
        return Sky.lerpColor(color | 0xFF000000, 0xFF8A1F55, 0.35f);
    }

    /**
     * {@code text} in {@code font}, outlined in white, as large as fits in w x h (down to minSize) on up to two lines,
     * as a centred drawable.
     */
    static BitmapDrawable label(Resources res, Typeface font, String text, int w, int h, float maxSize, float minSize,
                                int color, float outline) {
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setTypeface(Typeface.create(font, Typeface.BOLD));
        fill.setColor(color);
        String[] lines = {text};
        float size = maxSize;
        for (; size >= minSize; size -= 1f) {
            fill.setTextSize(size);
            lines = wrap(fill, text, w - outline * 2);
            if (lines != null && lines.length * size * 1.15f <= h) break;
        }
        if (lines == null) lines = new String[]{text};
        size = Math.max(size, minSize);
        fill.setTextSize(size);
        float widest = 0f;
        for (String l : lines) widest = Math.max(widest, fill.measureText(l));
        int bw = (int) Math.ceil(Math.min(widest, w) + outline * 2 + 2), bh = (int) Math.ceil(lines.length * size * 1.15f + outline * 2);
        Bitmap b = Bitmap.createBitmap(Math.max(1, bw), Math.max(1, bh), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint stroke = new Paint(fill);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(outline * 2);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setColor(0xFFFFFFFF);
        fill.setTextAlign(Paint.Align.CENTER);
        stroke.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = fill.getFontMetrics();
        for (int i = 0; i < lines.length; i++) {
            float baseline = outline + i * size * 1.15f + (size * 1.15f - (fm.descent - fm.ascent)) / 2f - fm.ascent;
            c.drawText(lines[i], bw / 2f, baseline, stroke);
            c.drawText(lines[i], bw / 2f, baseline, fill);
        }
        BitmapDrawable d = new BitmapDrawable(res, b);
        d.setGravity(Gravity.CENTER);
        return d;
    }

    /** {@code text} split at spaces into lines no wider than w, or null when one word is too wide or it takes over two. */
    @Nullable
    private static String[] wrap(Paint p, String text, float w) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split(" +")) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (p.measureText(next) <= w) {
                line.setLength(0);
                line.append(next);
            } else {
                if (p.measureText(word) > w) return null;
                if (line.length() > 0) lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines.size() <= 2 ? lines.toArray(new String[0]) : null;
    }

    /** Frame styles, cycled along the row so neighbouring cards differ: bow, hearts, stars, music notes, dots. */
    static final int STYLES = 5;

    /** The theme's own bow.png, scaled for a tile's corner, or null to draw the bow; set by AppAdapter. */
    @Nullable static android.graphics.Bitmap bowPicture;

    /** A card: cream fill and one of {@link #STYLES} decorated pastel frames, framed in the app's own colour. */
    static final class Frame extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path path = new Path();
        private final int color, style, fillAlpha;
        private final float density;

        /** {@code fillAlpha} (0-255, from dock_opacity) fades only the card's fill, so the scene shows through it. */
        Frame(int color, int style, float density, int fillAlpha) {
            this.color = pastel(color);
            this.style = style % STYLES;
            this.density = density;
            this.fillAlpha = fillAlpha;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect b = getBounds();
            float r = AppAdapter.cornerRadius(b.width(), density), d = density;
            rect.set(b);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            if (style == 3) {
                // Music notes: a mint-to-pink card.
                paint.setShader(new android.graphics.LinearGradient(b.left, b.top, b.right, b.bottom,
                        0xFFD9F5EA, 0xFFFAD6E8, android.graphics.Shader.TileMode.CLAMP));
            } else {
                paint.setColor(style == 4 ? 0xFFF6EEFF : 0xFFFFF7FA);
            }
            paint.setAlpha(fillAlpha);
            canvas.drawRoundRect(rect, r, r, paint);
            paint.setAlpha(255);
            paint.setShader(null);

            int frame = style == 2 ? 0xFFC9B3F2 : style == 3 ? 0xFFB5E3D2 : style == 4 ? 0xFFD7C2F5 : color;
            if (style == 1) {
                // A border of little hearts all round, over a thin line.
                stroke(canvas, 1.5f * d, 0xFFF7A8C4, 3f * d, r);
                float step = 13f * d, inset = 6f * d;
                for (float x = b.left + inset + step / 2; x < b.right - inset; x += step) {
                    heart(canvas, x, b.top + inset, 3.4f * d, 0xFFEF5A84);
                    heart(canvas, x, b.bottom - inset, 3.4f * d, 0xFFEF5A84);
                }
                for (float y = b.top + inset + step; y < b.bottom - inset - step / 2; y += step) {
                    heart(canvas, b.left + inset, y, 3.4f * d, 0xFFEF5A84);
                    heart(canvas, b.right - inset, y, 3.4f * d, 0xFFEF5A84);
                }
            } else if (style == 4) {
                // A dotted border.
                stroke(canvas, 2f * d, frame, 2f * d, r);
                paint.setColor(0xFFB89AE8);
                float step = 9f * d, inset = 7f * d;
                for (float x = b.left + inset; x <= b.right - inset; x += step) {
                    canvas.drawCircle(x, b.top + inset, 1.6f * d, paint);
                    canvas.drawCircle(x, b.bottom - inset, 1.6f * d, paint);
                }
                for (float y = b.top + inset + step; y < b.bottom - inset; y += step) {
                    canvas.drawCircle(b.left + inset, y, 1.6f * d, paint);
                    canvas.drawCircle(b.right - inset, y, 1.6f * d, paint);
                }
            } else {
                stroke(canvas, 4f * d, frame, 2f * d, r);
                stroke(canvas, 1.2f * d, (frame & 0xFFFFFF) | 0x99000000, 7f * d, r * 0.7f);
            }

            if (style == 0) {
                bow(canvas, b.right - 16f * d, b.top + 13f * d, 7f * d);
            } else if (style == 2) {
                // Butter-yellow stars in the corners.
                float o = 12f * d;
                star(canvas, b.left + o, b.top + o, 6f * d, 0xFFFFD45C);
                star(canvas, b.right - o, b.top + o, 5f * d, 0xFFFFD45C);
                star(canvas, b.left + o, b.bottom - o, 5f * d, 0xFFFFD45C);
                star(canvas, b.right - o, b.bottom - o, 6f * d, 0xFFFFD45C);
            } else if (style == 3) {
                note(canvas, b.left + 16f * d, b.top + 20f * d, d, 0xFFE07AA6);
                note(canvas, b.right - 20f * d, b.top + 18f * d, d, 0xFF8FCFB8);
                note(canvas, b.right - 26f * d, b.bottom - 14f * d, d, 0xFFE07AA6);
            } else if (style == 4) {
                star(canvas, b.right - 14f * d, b.top + 14f * d, 4.5f * d, 0xFFB89AE8);
                heart(canvas, b.left + 15f * d, b.bottom - 15f * d, 4f * d, 0xFFF29AC0);
            }
        }

        private void stroke(Canvas canvas, float width, int c, float inset, float r) {
            rect.set(getBounds());
            rect.inset(inset, inset);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(width);
            paint.setColor(c);
            canvas.drawRoundRect(rect, r, r, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void heart(Canvas c, float x, float y, float s, int col) {
            path.reset();
            path.moveTo(x, y + s);
            path.cubicTo(x - s * 1.6f, y - s * 0.2f, x - s * 0.8f, y - s * 1.3f, x, y - s * 0.45f);
            path.cubicTo(x + s * 0.8f, y - s * 1.3f, x + s * 1.6f, y - s * 0.2f, x, y + s);
            path.close();
            paint.setColor(col);
            c.drawPath(path, paint);
        }

        private void star(Canvas c, float x, float y, float s, int col) {
            path.reset();
            for (int i = 0; i < 10; i++) {
                double a = Math.PI / 2 + i * Math.PI / 5;
                float rr = i % 2 == 0 ? s : s * 0.45f;
                float px = x + (float) Math.cos(a) * rr, py = y - (float) Math.sin(a) * rr;
                if (i == 0) path.moveTo(px, py);
                else path.lineTo(px, py);
            }
            path.close();
            paint.setColor(col);
            c.drawPath(path, paint);
        }

        /** A quaver: head, stem and flag. */
        private void note(Canvas c, float x, float y, float d, int col) {
            paint.setColor(col);
            c.save();
            c.rotate(-20f, x, y);
            c.drawOval(x - 3.2f * d, y - 2.2f * d, x + 3.2f * d, y + 2.2f * d, paint);
            c.restore();
            c.drawRect(x + 2.2f * d, y - 11f * d, x + 3.4f * d, y, paint);
            path.reset();
            path.moveTo(x + 3.4f * d, y - 11f * d);
            path.quadTo(x + 8f * d, y - 8f * d, x + 6.5f * d, y - 4f * d);
            path.quadTo(x + 6f * d, y - 7f * d, x + 3.4f * d, y - 7.5f * d);
            path.close();
            c.drawPath(path, paint);
        }

        /** The red bow: the theme's bow.png when it has one (bowPicture), else two drawn loops and a knot. */
        private void bow(Canvas canvas, float cx, float cy, float s) {
            if (bowPicture != null) {
                canvas.drawBitmap(bowPicture, cx - bowPicture.getWidth() / 2f, cy - bowPicture.getHeight() / 2f, null);
                return;
            }
            paint.setColor(0xFFF0506E);
            path.reset();
            path.moveTo(cx, cy);
            path.cubicTo(cx - s * 0.6f, cy - s * 1.1f, cx - s * 1.6f, cy - s * 0.6f, cx - s * 1.5f, cy);
            path.cubicTo(cx - s * 1.6f, cy + s * 0.6f, cx - s * 0.6f, cy + s * 1.1f, cx, cy);
            path.cubicTo(cx + s * 0.6f, cy - s * 1.1f, cx + s * 1.6f, cy - s * 0.6f, cx + s * 1.5f, cy);
            path.cubicTo(cx + s * 1.6f, cy + s * 0.6f, cx + s * 0.6f, cy + s * 1.1f, cx, cy);
            path.close();
            canvas.drawPath(path, paint);
            paint.setColor(0xFFC8344F);
            canvas.drawCircle(cx, cy, s * 0.42f, paint);
            paint.setColor(0x66FFFFFF);
            canvas.drawCircle(cx - s * 0.9f, cy - s * 0.25f, s * 0.22f, paint);
            canvas.drawCircle(cx + s * 0.9f, cy - s * 0.25f, s * 0.22f, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
