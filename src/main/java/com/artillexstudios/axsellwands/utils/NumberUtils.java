package com.artillexstudios.axsellwands.utils;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

import static com.artillexstudios.axsellwands.AxSellwands.CONFIG;

public class NumberUtils {
    private static volatile NumberFormat formatter = new DecimalFormat(CONFIG.getString("number-formatting.formatted", "#,###.##"));

    public static void reload() {
        int mode = CONFIG.getInt("number-formatting.mode", 0);
        switch (mode) {
            case 0 -> formatter = new DecimalFormat(CONFIG.getString("number-formatting.formatted", "#,###.##"));
            case 1 -> {
                final String[] lang = CONFIG.getString("number-formatting.short", "en_US").split("_");
                formatter = DecimalFormat.getCompactNumberInstance(Locale.of(lang[0], lang[1]), NumberFormat.Style.SHORT);
            }
            case 2 -> formatter = null;
        }
    }

    /**
     * Formats a number for display. Sellwand lore is rendered on packet threads as well as server
     * threads, and a {@link NumberFormat} keeps parsing state between calls, so one formatter
     * cannot serve two callers at once.
     */
    public static String formatNumber(double number) {
        NumberFormat current = formatter;
        if (current == null) {
            return "" + number;
        }

        synchronized (current) {
            return current.format(number);
        }
    }
}
