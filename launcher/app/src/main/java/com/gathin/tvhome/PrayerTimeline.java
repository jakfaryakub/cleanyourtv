package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

import java.util.Locale;

/**
 * prayer_style "timeline": a thin line for the day under the date, a dot for each of the five prayers, a tick for
 * now, and one label, under the next prayer, whose dot glows in prayer_glow_color. Drawn in onDraw only, with no
 * bitmap and no layer; MainActivity hands it a new PrayerTimelineModel once a minute (minuteTick) and at each
 * prayer, and it redraws only when that changed. header_shadow gives every mark a dark edge and the label a shadow,
 * so it reads over a light wallpaper.
 */
final class PrayerTimeline extends View {
    private static final int LINE = 0x59FFFFFF, LINE_DONE = 0x99FFFFFF, TICK = 0x40FFFFFF;
    private static final int DOT = 0xE6FFFFFF, DOT_PASSED = 0x59FFFFFF, EDGE = 0x66000000;

    private final float density;
    private final int glowColor;
    private final float widthPx, pad, lineY, dotR, nextR, glowR, labelGap;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean shadow;
    private PrayerTimelineModel model;

    PrayerTimeline(Context context, float textSizePx, Typeface bold, int glowColor) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        this.glowColor = glowColor | 0xFF000000;
        widthPx = 285 * density;
        glowR = 15 * density;
        pad = glowR;
        lineY = glowR;
        dotR = 3.5f * density;
        nextR = 4.5f * density;
        labelGap = 9 * density;
        int clear = this.glowColor & 0x00FFFFFF;
        glow.setShader(new RadialGradient(0, 0, glowR, new int[] {(0xD9 << 24) | clear, (0x66 << 24) | clear, clear},
                new float[] {0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        text.setTextSize(textSizePx);
        text.setColor(0xFFFFFFFF);
        text.setTypeface(bold);
        text.setTextAlign(Paint.Align.CENTER);
        text.setLetterSpacing(0.02f);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    /** header_shadow: a dark edge under the marks and a soft shadow under the label, as on the clock and date. */
    void setShadow(boolean on) {
        shadow = on;
        if (on) text.setShadowLayer(6 * density, 0, 1.5f * density, 0x99000000);
        else text.clearShadowLayer();
        invalidate();
    }

    void show(PrayerTimelineModel next) {
        if (next.sameAs(model)) return;
        model = next;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        Paint.FontMetrics fm = text.getFontMetrics();
        float height = lineY + labelGap + (fm.descent - fm.ascent) + 8 * density;
        setMeasuredDimension(resolveSize(Math.round(widthPx), widthSpec), Math.round(height));
    }

    private float x(int minute) {
        return pad + PrayerTimelineModel.fraction(minute) * (getWidth() - 2 * pad);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        PrayerTimelineModel m = model;
        if (m == null) return;
        float stroke = Math.max(1f, density), left = x(0), right = x(PrayerTimelineModel.DAY), nowX = x(m.now);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        if (shadow) {
            paint.setStrokeWidth(stroke + 2 * density);
            paint.setColor(EDGE);
            canvas.drawLine(left, lineY, right, lineY, paint);
        }
        paint.setStrokeWidth(stroke);
        paint.setColor(LINE);
        canvas.drawLine(nowX, lineY, right, lineY, paint);
        paint.setColor(LINE_DONE);
        canvas.drawLine(left, lineY, nowX, lineY, paint);
        paint.setColor(TICK);
        float tick = 2.5f * density;
        for (int h = 0; h <= 24; h += 6) {
            float tx = x(h * 60);
            canvas.drawLine(tx, lineY - tick, tx, lineY + tick, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < m.times.length; i++) {
            if (m.times[i] == Prayer.NONE || i == m.next) continue;
            float dx = x(m.times[i]);
            if (shadow) {
                paint.setColor(EDGE);
                canvas.drawCircle(dx, lineY, dotR + 1.5f * density, paint);
            }
            paint.setColor(m.passed[i] ? DOT_PASSED : DOT);
            canvas.drawCircle(dx, lineY, dotR, paint);
        }
        // Now: a short upright tick, white over everything but the glow.
        paint.setStyle(Paint.Style.STROKE);
        float half = 6 * density;
        if (shadow) {
            paint.setStrokeWidth(1.5f * density + 2 * density);
            paint.setColor(EDGE);
            canvas.drawLine(nowX, lineY - half, nowX, lineY + half, paint);
        }
        paint.setStrokeWidth(1.5f * density);
        paint.setColor(0xFFFFFFFF);
        canvas.drawLine(nowX, lineY - half, nowX, lineY + half, paint);
        if (m.next < 0) return;
        float gx = x(m.nextAt);
        canvas.save();
        canvas.translate(gx, lineY);
        canvas.drawCircle(0, 0, glowR, glow);
        canvas.restore();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(glowColor);   // after Isha too: tomorrow's Fajr is still to come
        canvas.drawCircle(gx, lineY, nextR, paint);
        String label = String.format(Locale.ROOT, "%s %02d:%02d", name(m.next), m.label / 60, m.label % 60);
        float half2 = text.measureText(label) / 2;
        float lx = Math.max(half2, Math.min(getWidth() - half2, gx));
        canvas.drawText(label, lx, lineY + labelGap - text.getFontMetrics().ascent, text);
    }

    private String[] names;

    private String name(int i) {
        if (names == null) names = getResources().getStringArray(R.array.prayer_names);
        return i < names.length ? names[i] : "";
    }
}
