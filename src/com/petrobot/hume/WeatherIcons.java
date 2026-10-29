package com.petrobot.hume;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.LruCache;

/**
 * Multicolour weather icons drawn in code, in the manner of Apple's current SF Symbols: flat
 * fills, no gradients or shading — a yellow sun, a white moon, white clouds (a grey one behind),
 * cyan rain, a yellow bolt. Keyed by WMO weather code.
 */
final class WeatherIcons {
    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(48);
    private static final int SUN = 0xFFFFD60A, MOON = 0xFFF2F2F7, CLOUD = 0xFFFFFFFF, CLOUD_BACK = 0xFFA1A7B3,
            RAIN = 0xFF5AC8FA, BOLT = 0xFFFFD60A;

    private WeatherIcons() {
    }

    static Bitmap get(int code, boolean day, int size) {
        String key = code + (day ? "d" : "n") + size;
        Bitmap b = CACHE.get(key);
        if (b != null) return b;
        b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        draw(new Canvas(b), code, day, size);
        CACHE.put(key, b);
        return b;
    }

    private static void draw(Canvas c, int code, boolean day, float s) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        switch (kind(code)) {
            case "clear":
                if (day) sun(c, p, s * 0.5f, s * 0.5f, s * 0.22f, true);
                else moon(c, p, s * 0.5f, s * 0.5f, s * 0.3f);
                break;
            case "partly":
                if (day) sun(c, p, s * 0.64f, s * 0.36f, s * 0.16f, true);
                else moon(c, p, s * 0.64f, s * 0.36f, s * 0.2f);
                cloud(c, p, s * 0.06f, s * 0.4f, s * 0.7f, false);
                break;
            case "cloudy":
                cloud(c, p, s * 0.3f, s * 0.2f, s * 0.62f, true);
                cloud(c, p, s * 0.06f, s * 0.36f, s * 0.74f, false);
                break;
            case "fog":
                cloud(c, p, s * 0.14f, s * 0.14f, s * 0.72f, false);
                p.setShader(null);
                p.setColor(0xCCE5E5EA);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeWidth(s * 0.06f);
                c.drawLine(s * 0.16f, s * 0.7f, s * 0.84f, s * 0.7f, p);
                c.drawLine(s * 0.26f, s * 0.84f, s * 0.74f, s * 0.84f, p);
                break;
            case "drizzle":
                cloud(c, p, s * 0.12f, s * 0.12f, s * 0.76f, false);
                drops(c, p, s, 3, 0.1f);
                break;
            case "rain":
                cloud(c, p, s * 0.12f, s * 0.12f, s * 0.76f, false);
                drops(c, p, s, 3, 0.17f);
                break;
            case "snow":
                cloud(c, p, s * 0.12f, s * 0.12f, s * 0.76f, false);
                p.setShader(null);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFFFFFF);
                float[][] f = {{0.32f, 0.74f}, {0.52f, 0.82f}, {0.72f, 0.74f}, {0.42f, 0.92f}, {0.62f, 0.94f}};
                for (float[] d : f) c.drawCircle(s * d[0], s * d[1], s * 0.045f, p);
                break;
            case "thunder":
                cloud(c, p, s * 0.12f, s * 0.1f, s * 0.76f, false);
                p.setShader(null);
                p.setStyle(Paint.Style.FILL);
                p.setColor(BOLT);
                Path bolt = new Path();
                bolt.moveTo(s * 0.54f, s * 0.56f);
                bolt.lineTo(s * 0.38f, s * 0.78f);
                bolt.lineTo(s * 0.5f, s * 0.78f);
                bolt.lineTo(s * 0.43f, s * 0.97f);
                bolt.lineTo(s * 0.64f, s * 0.7f);
                bolt.lineTo(s * 0.52f, s * 0.7f);
                bolt.lineTo(s * 0.6f, s * 0.56f);
                bolt.close();
                c.drawPath(bolt, p);
                break;
            default:
                cloud(c, p, s * 0.12f, s * 0.24f, s * 0.76f, false);
        }
    }

    static String kind(int code) {
        if (code == 0) return "clear";
        if (code == 1 || code == 2) return "partly";
        if (code == 3) return "cloudy";
        if (code == 45 || code == 48) return "fog";
        if (code >= 51 && code <= 57) return "drizzle";
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return "rain";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "snow";
        if (code >= 95) return "thunder";
        return "cloudy";
    }

    private static void sun(Canvas c, Paint p, float cx, float cy, float r, boolean rays) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setColor(SUN);
        c.drawCircle(cx, cy, r, p);
        if (!rays) return;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(r * 0.28f);
        p.setShader(null);
        p.setColor(SUN);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            float x0 = (float) Math.cos(a), y0 = (float) Math.sin(a);
            c.drawLine(cx + x0 * r * 1.45f, cy + y0 * r * 1.45f, cx + x0 * r * 1.85f, cy + y0 * r * 1.85f, p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    private static void moon(Canvas c, Paint p, float cx, float cy, float r) {
        Path full = new Path(), bite = new Path();
        full.addCircle(cx, cy, r, Path.Direction.CW);
        bite.addCircle(cx + r * 0.55f, cy - r * 0.4f, r * 0.82f, Path.Direction.CW);
        full.op(bite, Path.Op.DIFFERENCE);
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setColor(MOON);
        c.drawPath(full, p);
    }

    /** A cloud w wide (0.6 w tall) with its top-left at (l, t). */
    private static void cloud(Canvas c, Paint p, float l, float t, float w, boolean back) {
        float h = w * 0.6f;
        Path path = new Path(), part = new Path();
        path.addCircle(l + w * 0.56f, t + h * 0.44f, w * 0.27f, Path.Direction.CW);
        part.addCircle(l + w * 0.3f, t + h * 0.62f, w * 0.19f, Path.Direction.CW);
        path.op(part, Path.Op.UNION);
        part.reset();
        part.addCircle(l + w * 0.8f, t + h * 0.68f, w * 0.16f, Path.Direction.CW);
        path.op(part, Path.Op.UNION);
        part.reset();
        part.addRoundRect(new RectF(l + w * 0.12f, t + h * 0.55f, l + w * 0.96f, t + h * 0.95f), h * 0.2f, h * 0.2f, Path.Direction.CW);
        path.op(part, Path.Op.UNION);
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        // A thin gap where the cloud overlaps the sun, moon or a cloud behind (as SF Symbols do).
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w * 0.07f);
        p.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR));
        c.drawPath(path, p);
        p.setXfermode(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(back ? CLOUD_BACK : CLOUD);
        c.drawPath(path, p);
    }

    private static void drops(Canvas c, Paint p, float s, int n, float len) {
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(s * 0.065f);
        p.setColor(RAIN);
        for (int i = 0; i < n; i++) {
            float x = s * (0.34f + i * 0.17f), y = s * 0.68f;
            c.drawLine(x, y, x - s * len * 0.4f, y + s * len, p);
        }
        p.setStyle(Paint.Style.FILL);
    }
}
