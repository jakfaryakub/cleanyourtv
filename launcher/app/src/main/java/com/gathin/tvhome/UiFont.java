package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.util.TypedValue;

/**
 * The typeface behind one of the font_* strings in res/values/fonts.xml, for views built in code. The string
 * points at a bundled font, or, in locales Outfit can't draw (values-vi), names a system family (#833). A theme
 * font (ThemeFont) takes its place when the theme names one; layouts get it through ThemeFontFactory.
 */
final class UiFont {
    private UiFont() {}

    static Typeface get(Context context, int fontString) {
        ThemeFont theme = ThemeFont.current();
        if (theme != null) {
            Typeface t = theme.get(weight(fontString));
            if (t != null) return t;
        }
        Resources res = context.getResources();
        TypedValue value = new TypedValue();
        res.getValue(fontString, value, true);
        String name = String.valueOf(value.string);
        if (name.startsWith("res/")) return res.getFont(value.resourceId);
        return Typeface.create(name, Typeface.NORMAL);
    }

    /**
     * The weight a font_* string stands for, by the string's id or by the font it resolves to (a layout's
     * android:fontFamily resolves @string/font_medium on to @font/outfit_medium); 0 for anything else.
     */
    static int weight(int id) {
        if (id == R.string.font_extralight || id == R.font.outfit_extralight) return 200;
        if (id == R.string.font_light || id == R.font.outfit_light) return 300;
        if (id == R.string.font_medium || id == R.font.outfit_medium) return 500;
        if (id == R.string.font_regular || id == R.font.outfit_regular
                || id == R.string.font_body || id == R.font.outfit) return 400;
        return 0;
    }
}
