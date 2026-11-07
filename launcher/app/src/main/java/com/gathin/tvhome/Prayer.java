package com.gathin.tvhome;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Today's five prayer times for a place (config.json prayer_times), computed offline with Kemenag RI's parameters:
 * Subuh with the sun 20 degrees below the horizon, Isya at 18, Ashar when a shadow is its object's length plus the
 * noon shadow (Shafi'i), Maghrib at -1.0 degrees (refraction, the sun's half width and the horizon's dip), Dzuhur at
 * the sun's transit plus 3 minutes and the others plus the ihtiyat, each rounded up to the minute.
 *
 * The sun's place comes from Meeus' low-precision formulas (Astronomical Algorithms, ch. 25 and 28), evaluated at
 * the time of each event and iterated, which keeps Dzuhur and Maghrib within the minute where {@link Sun}'s
 * day-of-year series is a minute or two off. Pure maths, no Android types and no network, so it runs in JVM tests:
 * PrayerTest holds it to Kemenag's published table for Jakarta. Like {@link Sun}, it needs no location permission;
 * the place comes from the theme.
 */
final class Prayer {
    static final int SUBUH = 0, DZUHUR = 1, ASHAR = 2, MAGHRIB = 3, ISYA = 4;
    static final int COUNT = 5;
    /** No time that day: the sun never reaches the angle (Isya in a high-latitude summer). */
    static final int NONE = -1;
    static final int IHTIYAT_DEFAULT = 2;
    static final int DZUHUR_EXTRA = 3;
    static final double SUBUH_ANGLE = -20, ISYA_ANGLE = -18, MAGHRIB_ALTITUDE = -1.0;

    private Prayer() {
    }

    /**
     * Local minutes of the day of Subuh, Dzuhur, Ashar, Maghrib and Isya, or {@link #NONE} for one that does not
     * happen that day. The ihtiyat (0-5 minutes) is added to all but Dzuhur, which takes {@link #DZUHUR_EXTRA}.
     */
    static int[] times(LocalDate date, double latitude, double longitude, ZoneId zone, int ihtiyat) {
        double tz = zone.getRules().getOffset(date.atTime(12, 0)).getTotalSeconds() / 3600.0;
        double lat = Math.toRadians(latitude);
        int[] out = new int[COUNT];
        out[SUBUH] = minute(event(date, lat, longitude, tz, SUBUH), ihtiyat);
        out[DZUHUR] = minute(event(date, lat, longitude, tz, DZUHUR), DZUHUR_EXTRA);
        out[ASHAR] = minute(event(date, lat, longitude, tz, ASHAR), ihtiyat);
        out[MAGHRIB] = minute(event(date, lat, longitude, tz, MAGHRIB), ihtiyat);
        out[ISYA] = minute(event(date, lat, longitude, tz, ISYA), ihtiyat);
        return out;
    }

    /**
     * The prayer the line marks as next at this minute of the day: the first one still to come, or Subuh (tomorrow's)
     * once Isya has passed. A time that does not happen is skipped.
     */
    static int next(int[] times, int nowMinute) {
        for (int i = 0; i < COUNT; i++) {
            if (times[i] != NONE && times[i] > nowMinute) return i;
        }
        return SUBUH;
    }

    private static int minute(double hours, int extra) {
        if (Double.isNaN(hours)) return NONE;
        return (int) Math.ceil(hours * 60 + extra - 1e-9);
    }

    /** Local time in hours of one event, or NaN when the sun never reaches its altitude that day. */
    private static double event(LocalDate date, double lat, double longitude, double tz, int which) {
        double jd0 = date.toEpochDay() + 2440587.5;   // Julian day at 0h UT
        double t = 12;
        for (int i = 0; i < 3; i++) {
            double[] sun = sun(jd0 + (t - tz) / 24);
            double decl = sun[0], eqTime = sun[1];
            double noon = 12 + (-4 * longitude - eqTime) / 60 + tz;
            if (which == DZUHUR) {
                t = noon;
                continue;
            }
            double altitude = altitude(which, lat, decl);
            double cosHa = (Math.sin(Math.toRadians(altitude)) - Math.sin(lat) * Math.sin(decl))
                    / (Math.cos(lat) * Math.cos(decl));
            if (cosHa > 1 || cosHa < -1) return Double.NaN;
            double ha = Math.toDegrees(Math.acos(cosHa)) / 15;
            t = which == SUBUH ? noon - ha : noon + ha;
        }
        return t;
    }

    private static double altitude(int which, double lat, double decl) {
        switch (which) {
            case SUBUH: return SUBUH_ANGLE;
            case ISYA: return ISYA_ANGLE;
            case MAGHRIB: return MAGHRIB_ALTITUDE;
            default:   // Ashar: a shadow its object's length longer than the noon shadow
                return Math.toDegrees(Math.atan(1 / (1 + Math.tan(Math.abs(lat - decl)))));
        }
    }

    /** The sun's declination (radians) and the equation of time (minutes) at this Julian day. */
    static double[] sun(double jd) {
        double t = (jd - 2451545.0) / 36525;
        double l0 = Math.toRadians((280.46646 + 36000.76983 * t) % 360);
        double m = Math.toRadians(357.52911 + 35999.05029 * t);
        double c = Math.toRadians((1.914602 - 0.004817 * t) * Math.sin(m) + 0.019993 * Math.sin(2 * m)
                + 0.000289 * Math.sin(3 * m));
        double omega = Math.toRadians(125.04 - 1934.136 * t);
        double lambda = l0 + c - Math.toRadians(0.00569 + 0.00478 * Math.sin(omega));
        double eps = Math.toRadians(23.439291 - 0.0130042 * t + 0.00256 * Math.cos(omega));
        double decl = Math.asin(Math.sin(eps) * Math.sin(lambda));
        double y = Math.pow(Math.tan(eps / 2), 2);
        double e = 0.016708634 - 0.000042037 * t;
        double eqTime = 4 * Math.toDegrees(y * Math.sin(2 * l0) - 2 * e * Math.sin(m)
                + 4 * e * y * Math.sin(m) * Math.cos(2 * l0) - 0.5 * y * y * Math.sin(4 * l0)
                - 1.25 * e * e * Math.sin(2 * m));
        return new double[]{decl, eqTime};
    }
}
