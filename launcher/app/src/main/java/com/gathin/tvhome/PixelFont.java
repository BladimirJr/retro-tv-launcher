package com.gathin.tvhome;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.view.Gravity;

import java.text.Normalizer;
import java.util.Locale;

/**
 * A 5x7 pixel font for the arcade theme, drawn as squares: capitals, digits and a little punctuation. Lower case
 * is shown as capitals and accents are dropped (Película as PELICULA); anything else becomes a space.
 */
final class PixelFont {

    private PixelFont() {
    }

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-.+!:&'/?";
    /** Seven rows per glyph, one base-32 digit per row whose five low bits are the pixels, left to right. */
    private static final String ROWS = "EHHVHHHUHHUHHUEHGGGHEUHHHHHUVGGUGGVVGGUGGGEHGNHHFHHHVHHHE44444E72222ICHIKOKIHGGGGGGVHRLLHHHHPLJHHHEHHHHHEUHHUGGGEHHHLIDUHHUKIHFGGE11UV444444HHHHHHEHHHHHA4HHHLLLAHHA4AHHHHA4444V1248GVEHJLPHE4C4444EEH1248VV2421HE26AIV22VGU11HE68GUHHEV124888EHHEHHEEHHF12C000V00000000CC044V44044444040CC0CC0CIK8LID4480000122488GEH12404";

    /** Width in pixels of {@code text} at {@code px} per font pixel, one pixel of space between letters. */
    static float width(String text, float px) {
        return Math.max(0, normalize(text).length() * 6 - 1) * px;
    }

    /** Capitals without accents, the only letters the font has. */
    static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
    }

    /** Draws {@code text} with its top-left corner at x, y; {@code shadow} (0 for none) one pixel down-right under it. */
    static void draw(Canvas c, String text, float x, float y, float px, int color, int shadow, Paint paint) {
        String s = normalize(text);
        for (int pass = shadow == 0 ? 1 : 0; pass < 2; pass++) {
            paint.setColor(pass == 0 ? shadow : color);
            float o = pass == 0 ? px : 0f;
            for (int i = 0; i < s.length(); i++) {
                int g = CHARS.indexOf(s.charAt(i));
                if (g < 0) continue;
                float gx = x + i * 6 * px + o;
                for (int row = 0; row < 7; row++) {
                    int bits = Character.digit(ROWS.charAt(g * 7 + row), 32);
                    for (int col = 0; col < 5; col++) {
                        if ((bits & (1 << (4 - col))) == 0) continue;
                        c.drawRect(gx + col * px, y + row * px + o, gx + (col + 1) * px, y + (row + 1) * px + o, paint);
                    }
                }
            }
        }
    }

    /**
     * {@code text} as a crisp, centred drawable at most {@code maxWidth} pixels wide: the font pixel shrinks to fit,
     * down to 3/5 of {@code px}, then the text is cut with "..". For a TextView's background, where it replaces the text.
     */
    static BitmapDrawable drawable(Resources res, String text, int maxWidth, int px, int color, int shadow) {
        String s = normalize(text);
        int minPx = Math.max(1, px * 3 / 5);   // smaller than this stops reading as text from the sofa
        while (px > minPx && width(s, px) + px > maxWidth) px--;
        while (s.length() > 3 && width(s, px) + px > maxWidth) s = s.substring(0, s.length() - 3) + "..";
        int w = (int) Math.ceil(width(s, px)) + px, h = 8 * px;
        Bitmap b = Bitmap.createBitmap(Math.max(1, w), h, Bitmap.Config.ARGB_8888);
        draw(new Canvas(b), s, 0f, 0f, px, color, shadow, new Paint());
        BitmapDrawable d = new BitmapDrawable(res, b);
        d.setGravity(Gravity.CENTER);
        d.setFilterBitmap(false);
        return d;
    }

    /**
     * {@code text} as large as it fits in {@code maxWidth} x {@code maxHeight}, wrapped onto up to two centred lines
     * at word breaks; the font pixel goes from {@code maxPx} down to {@code minPx}, and a name that still does not
     * fit is cut with "..". For a tile whose whole face is the app's name.
     */
    static BitmapDrawable block(Resources res, String text, int maxWidth, int maxHeight, int maxPx, int minPx,
                                int color, int shadow) {
        String s = normalize(text).trim();
        for (int px = maxPx; px >= minPx; px--) {
            String[] lines = wrap(s, px, maxWidth);
            if (lines != null && lines.length * 9 * px <= maxHeight) return render(res, lines, px, color, shadow);
        }
        int px = Math.max(1, minPx);
        String cut = s;
        while (cut.length() > 3 && width(cut, px) + px > maxWidth) cut = cut.substring(0, cut.length() - 3) + "..";
        return render(res, new String[]{cut}, px, color, shadow);
    }

    /** {@code s} greedily split at spaces into lines no wider than maxWidth, or null if that takes over two lines. */
    private static String[] wrap(String s, int px, int maxWidth) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" +")) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (width(next, px) + px <= maxWidth) {
                line.setLength(0);
                line.append(next);
            } else {
                if (width(word, px) + px > maxWidth) return null;
                if (line.length() > 0) lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines.size() <= 2 ? lines.toArray(new String[0]) : null;
    }

    private static BitmapDrawable render(Resources res, String[] lines, int px, int color, int shadow) {
        float widest = 0f;
        for (String l : lines) widest = Math.max(widest, width(l, px));
        int w = (int) Math.ceil(widest) + px, h = lines.length * 9 * px - px;
        Bitmap b = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint paint = new Paint();
        for (int i = 0; i < lines.length; i++) {
            draw(c, lines[i], (w - px - width(lines[i], px)) / 2f, i * 9 * px, px, color, shadow, paint);
        }
        BitmapDrawable d = new BitmapDrawable(res, b);
        d.setGravity(Gravity.CENTER);
        d.setFilterBitmap(false);
        return d;
    }
}
