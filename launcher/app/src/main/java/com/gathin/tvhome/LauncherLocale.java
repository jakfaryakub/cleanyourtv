package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Configuration;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * locale: the language the launcher shows itself in, instead of the TV's. A China-market Xiaomi offers Chinese and
 * English only, so a Vietnamese owner saw the launcher in Chinese although it is translated into Vietnamese.
 * Each activity wraps its base context here; an empty or malformed tag leaves the TV's language.
 */
final class LauncherLocale {
    // Subtags after the language, so "sr-Latn" and "ar-EG-u-nu-latn" (Arabic with Western digits) pass too.
    private static final Pattern TAG = Pattern.compile("[a-z]{2,3}(-[A-Za-z0-9]{1,8})*");

    private LauncherLocale() {
    }

    /** The Locale for a config tag such as "vi", "pt-BR" or "ar-EG-u-nu-latn", or null for "" or anything that is not a plain tag. */
    static Locale parse(String tag) {
        if (tag == null || !TAG.matcher(tag.trim()).matches()) return null;
        return Locale.forLanguageTag(tag.trim());
    }

    /**
     * base with the configured language and font_scale, or base itself when both are off. font_scale multiplies the
     * TV's own fontScale, so every sp size follows, the ones set in code included; dp sizes do not move.
     *
     * Both are read once, when an activity is created. A theme chosen on the TV with the remote (Fase 3 in
     * docs/PLAN.md) must recreate() the activities it changes, or a new scale or language shows only after the next
     * restart of Home.
     */
    static Context wrap(Context base) {
        Config cfg = Config.load(base);
        Locale locale = parse(cfg.locale);
        if (locale == null && cfg.fontScale == 1f) return base;
        Configuration c = new Configuration(base.getResources().getConfiguration());
        if (locale != null) {
            // Formatters that take no Context (the clock's date, the lunar line) follow the default locale.
            Locale.setDefault(locale);
            c.setLocale(locale);
        }
        if (cfg.fontScale != 1f) c.fontScale *= cfg.fontScale;
        return base.createConfigurationContext(c);
    }
}
