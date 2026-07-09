package com.artillexstudios.axsellwands.utils;

import static com.artillexstudios.axsellwands.AxSellwands.LANG;

/**
 * Renders the durations behind the %max-time% and %time-left% sellwand lore placeholders.
 */
public final class TimeUtils {
    private static final long SECOND = 1_000L;
    private static final long MINUTE = 60L * SECOND;
    private static final long HOUR = 60L * MINUTE;
    private static final long DAY = 24L * HOUR;

    private TimeUtils() {
    }

    /**
     * Formats a duration as its two largest non-zero units, for example {@code 12h 30m}.
     * Durations at or below zero render the configured "expired" text instead.
     */
    public static String format(long millis) {
        if (millis <= 0) return LANG.getString("time.expired", "Expired");

        long days = millis / DAY;
        long hours = (millis % DAY) / HOUR;
        long minutes = (millis % HOUR) / MINUTE;
        long seconds = (millis % MINUTE) / SECOND;

        if (days > 0) return append(unit("time.day", "%value%d", days), "time.hour", "%value%h", hours);
        if (hours > 0) return append(unit("time.hour", "%value%h", hours), "time.minute", "%value%m", minutes);
        if (minutes > 0) return append(unit("time.minute", "%value%m", minutes), "time.second", "%value%s", seconds);
        return unit("time.second", "%value%s", seconds);
    }

    /**
     * The text shown by both time placeholders for sellwands that never expire.
     */
    public static String never() {
        return LANG.getString("time.never", "∞");
    }

    private static String append(String head, String key, String fallback, long value) {
        if (value == 0) return head;
        return head + LANG.getString("time.separator", " ") + unit(key, fallback, value);
    }

    private static String unit(String key, String fallback, long value) {
        return LANG.getString(key, fallback).replace("%value%", Long.toString(value));
    }
}
