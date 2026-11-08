package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The themes kept on the TV as one row of cards (docs/PLAN.md, Phase 3, variant A): each slot, the root theme as
 * "Legacy" when it has a config.json, and "Reset to built-in". OK writes active_theme and returns RESULT_OK;
 * Launcher settings then closes, and Home recreates itself because the settings revision moved, which is what a
 * new font, font_scale or language needs. Only the theme in use is ever loaded; the cards show cached thumbnails.
 */
public final class ThemePickerActivity extends Activity {
    /** Set on the result when the theme changed. */
    static final String EXTRA_THEME_CHANGED = "com.gathin.tvhome.extra.THEME_CHANGED";
    private static final int CARD_W = 256;   // dp, 16:9 picture
    private static final int CARD_H = 144;
    private static final float FOCUSED_SCALE = 1.08f;
    private static final float UNFOCUSED_ALPHA = 0.6f;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Bitmap> thumbs = new ArrayList<>();
    private Thread loader;
    private volatile boolean destroyed;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_theme_picker);
        LinearLayout row = findViewById(R.id.themeRow);
        File root = Config.root(this);
        String active = ThemeSlots.active(root, Config.dir(this));
        List<ThemeSlots.Info> themes = ThemeSlots.list(root);
        List<ImageView> pictures = new ArrayList<>();
        View focus = null;
        for (ThemeSlots.Info t : themes) {
            boolean legacy = ThemeFiles.LEGACY.equals(t.id);
            String title = legacy ? getString(R.string.theme_legacy) : t.name;
            String size = Formatter.formatShortFileSize(this, t.bytes);
            String detail = !t.usable ? getString(R.string.theme_broken)
                    : legacy && !t.name.equals(t.id) ? t.name + " · " + size
                    : getString(t.animated ? R.string.theme_animated : R.string.theme_static, size);
            View card = card(row, title, detail, t.id.equals(active), pictures);
            card.setOnClickListener(v -> {
                if (!t.usable) Toast.makeText(this, R.string.theme_broken, Toast.LENGTH_SHORT).show();
                else apply(root, t.id, active);
            });
            if (t.id.equals(active)) focus = card;
        }
        View builtin = card(row, getString(R.string.theme_builtin), getString(R.string.theme_builtin_detail),
                ThemeFiles.BUILTIN.equals(active), pictures);
        builtin.setOnClickListener(v -> apply(root, ThemeFiles.BUILTIN, active));
        if (focus == null) focus = ThemeFiles.BUILTIN.equals(active) || themes.isEmpty() ? builtin : row.getChildAt(0);
        focus.requestFocus();
        loadThumbs(themes, pictures);
    }

    private void apply(File root, String id, String active) {
        if (id.equals(active)) {
            finish();
            return;
        }
        if (!ThemeSlots.write(root, id)) {
            Toast.makeText(this, R.string.theme_failed, Toast.LENGTH_LONG).show();
            return;
        }
        setResult(RESULT_OK, new android.content.Intent().putExtra(EXTRA_THEME_CHANGED, true));
        finish();
    }

    /** One card: the picture (filled in later), its name with an "In use" tag, and one line of detail. */
    private View card(LinearLayout row, String title, String detail, boolean inUse, List<ImageView> pictures) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setFocusable(true);
        card.setClickable(true);
        card.setClipChildren(false);
        int pad = dp(14);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(CARD_W) + 2 * pad,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(8));
        row.addView(card, lp);

        FrameLayout frame = new FrameLayout(this);
        GradientDrawable plain = new GradientDrawable();
        plain.setColor(0xFF26303A);
        plain.setCornerRadius(dp(8));
        frame.setBackground(plain);
        frame.setClipToOutline(true);
        ImageView picture = new ImageView(this);
        picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frame.addView(picture, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        card.addView(frame, new LinearLayout.LayoutParams(dp(CARD_W), dp(CARD_H)));
        pictures.add(picture);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(title, 18, 0xFFFFFFFF);
        titleRow.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (inUse) {
            TextView tag = text(getString(R.string.theme_in_use), 13, 0xFFFFFFFF);
            GradientDrawable pill = new GradientDrawable();
            pill.setColor(0x33FFFFFF);
            pill.setCornerRadius(dp(4));
            tag.setBackground(pill);
            tag.setPadding(dp(6), dp(1), dp(6), dp(1));
            titleRow.addView(tag);
        }
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(10);
        card.addView(titleRow, tp);
        card.addView(text(detail, 14, 0xB3FFFFFF));

        GradientDrawable ring = new GradientDrawable();
        ring.setColor(Color.TRANSPARENT);
        ring.setStroke(dp(3), 0xFFFFFFFF);
        ring.setCornerRadius(dp(8));
        card.setAlpha(UNFOCUSED_ALPHA);
        card.setOnFocusChangeListener((v, has) -> {
            frame.setForeground(has ? ring : null);
            v.animate().scaleX(has ? FOCUSED_SCALE : 1f).scaleY(has ? FOCUSED_SCALE : 1f)
                    .alpha(has ? 1f : UNFOCUSED_ALPHA).setDuration(150).start();
        });
        return card;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    /** Decodes the cards' pictures one at a time off the UI thread, from the cache when it has them. */
    private void loadThumbs(List<ThemeSlots.Info> themes, List<ImageView> pictures) {
        loader = new Thread(() -> {
            for (int i = 0; i < themes.size() && !destroyed; i++) {
                ThemeSlots.Info t = themes.get(i);
                Bitmap b = ThemeThumbs.load(this, t.id, t.folder);
                if (b == null) continue;
                ImageView view = pictures.get(i);
                main.post(() -> {
                    if (destroyed) {
                        b.recycle();
                        return;
                    }
                    thumbs.add(b);
                    view.setImageBitmap(b);
                });
            }
        }, "theme-thumbs");
        loader.setPriority(Thread.MIN_PRIORITY);
        loader.start();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (loader != null) loader.interrupt();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
        for (Bitmap b : thumbs) b.recycle();
        thumbs.clear();
    }
}
