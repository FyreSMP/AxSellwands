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

    private static volatile Formats formats = new Formats("%value%d", "%value%h", "%value%m", "%value%s", " ", "Expired", "∞");

    private TimeUtils() {
    }

    private record Formats(String day, String hour, String minute, String second, String separator, String expired,
                           String never) {}

    /**
     * Snapshots the configured wording. Expiring sellwand lore is rendered per item packet, so the
     * wording is read once here rather than walked out of the language file per call. Swapping one
     * record keeps a render from mixing wording from either side of a reload.
     */
    public static void reload() {
        formats = new Formats(LANG.getString("time.day", "%value%d"), LANG.getString("time.hour", "%value%h"), LANG.getString("time.minute", "%value%m"), LANG.getString("time.second", "%value%s"), LANG.getString("time.separator", " "), LANG.getString("time.expired", "Expired"), LANG.getString("time.never", "∞"));
    }

    /**
     * Formats a duration as its two largest non-zero units, for example {@code 12h 30m}.
     * Durations at or below zero render the configured "expired" text instead.
     */
    public static String format(long millis) {
        Formats current = formats;
        if (millis <= 0) {
            return current.expired();
        }

        long days = millis / DAY;
        long hours = (millis % DAY) / HOUR;
        long minutes = (millis % HOUR) / MINUTE;
        long seconds = (millis % MINUTE) / SECOND;

        if (days > 0) {
            return append(current, unit(current.day(), days), current.hour(), hours);
        }
        if (hours > 0) {
            return append(current, unit(current.hour(), hours), current.minute(), minutes);
        }
        if (minutes > 0) {
            return append(current, unit(current.minute(), minutes), current.second(), seconds);
        }
        return unit(current.second(), seconds);
    }

    /**
     * The text shown by both time placeholders for sellwands that never expire.
     */
    public static String never() {
        return formats.never();
    }

    private static String append(Formats current, String head, String format, long value) {
        if (value == 0) {
            return head;
        }
        return head + current.separator() + unit(format, value);
    }

    private static String unit(String format, long value) {
        return format.replace("%value%", Long.toString(value));
    }
}
