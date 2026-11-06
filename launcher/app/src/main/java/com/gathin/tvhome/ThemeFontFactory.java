package com.gathin.tvhome;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.InflateException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

/**
 * Gives every inflated view whose android:fontFamily is one of the font_* strings the theme font at the same weight.
 * Installed on an activity's (or the screensaver's) window inflater only while a theme font is loaded, so with no
 * font key nothing changes. Dialogs, popups and the App library inflate through the activity's inflater, and
 * LayoutInflater.cloneInContext copies the factory, so they are covered too; views built in code use UiFont.get.
 *
 * android:fontFamily is read with obtainStyledAttributes rather than straight off the AttributeSet, so a
 * fontFamily that comes from a style (TopButtonBase) counts as well as one written on the view. Only those views
 * are created here; for every other tag the factory returns null and the inflater carries on as it always did.
 */
final class ThemeFontFactory implements LayoutInflater.Factory2 {
    private static final int[] FONT_FAMILY = {android.R.attr.fontFamily};
    private static final String[] PREFIXES = {"android.widget.", "android.view.", "android.webkit."};

    private final ThemeFont font;

    private ThemeFontFactory(ThemeFont font) {
        this.font = font;
    }

    /** Before setContentView. Does nothing without a theme font, or if the inflater already has a factory. */
    static void install(Window window, ThemeFont font) {
        if (font == null || window == null) return;
        LayoutInflater inflater = window.getLayoutInflater();
        if (inflater.getFactory2() != null || inflater.getFactory() != null) return;
        inflater.setFactory2(new ThemeFontFactory(font));
    }

    @Override
    public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
        int weight = weight(context, attrs);
        if (weight == 0) return null;
        Typeface typeface = font.get(weight);
        if (typeface == null) return null;
        View view = create(name, context, attrs);
        if (view instanceof TextView) ((TextView) view).setTypeface(typeface);
        return view;
    }

    @Override
    public View onCreateView(String name, Context context, AttributeSet attrs) {
        return onCreateView(null, name, context, attrs);
    }

    // FONT_FAMILY is a one-attribute array of our own, not an R.styleable, so index 0 is fontFamily.
    @SuppressLint("ResourceType")
    private static int weight(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs, FONT_FAMILY);
        try {
            TypedValue v = a.peekValue(0);
            return v == null ? 0 : UiFont.weight(v.resourceId);
        } finally {
            a.recycle();
        }
    }

    /** The view the inflater would have made: a bare tag is looked up in the framework's packages, in its order. */
    private static View create(String name, Context context, AttributeSet attrs) {
        LayoutInflater inflater = LayoutInflater.from(context);
        try {
            if (name.indexOf('.') >= 0) return inflater.createView(name, null, attrs);
            for (String prefix : PREFIXES) {
                try {
                    return inflater.createView(name, prefix, attrs);
                } catch (ClassNotFoundException ignored) {
                    // next package
                }
            }
        } catch (ClassNotFoundException | InflateException e) {
            return null;
        }
        return null;
    }
}
