package com.warpmc.afk.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses duration strings into minutes.
 * Supports: plain number (minutes), and suffixed forms like 4h, 30m, 1d, 90s,
 * plus combos like 1d2h, 4h30m, 1h30m15s (case-insensitive, whitespace ignored).
 * Units: d/day(s), h/hour(s), m/min(s)/minute(s), s/sec(s)/second(s)
 * Seconds are ceiled to minutes.
 */
public final class DurationParser {

    private DurationParser() {}

    // matches number + unit, e.g. 4h, 30 minutes, 1d — longer alternatives first to avoid partial matches (hours before h)
    private static final Pattern TOKEN = Pattern.compile("(\\d+)\\s*(days?|d|hours?|h|minutes?|mins?|m|seconds?|secs?|s)", Pattern.CASE_INSENSITIVE);

    public static long parseMinutes(String input) throws NumberFormatException {
        if (input == null) throw new NumberFormatException("null");
        String s = input.trim().toLowerCase();
        if (s.isEmpty()) throw new NumberFormatException("empty");

        // plain integer -> minutes
        if (s.matches("\\d+")) {
            return Long.parseLong(s);
        }

        Matcher matcher = TOKEN.matcher(s);
        long totalMinutes = 0;
        boolean found = false;
        while (matcher.find()) {
            found = true;
            long val = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2).toLowerCase();
            char c = unit.charAt(0); // d/h/m/s
            switch (c) {
                case 'd' -> totalMinutes += val * 1440L;
                case 'h' -> totalMinutes += val * 60L;
                case 'm' -> totalMinutes += val;
                case 's' -> totalMinutes += (val + 59) / 60; // ceil seconds to minutes, at least 1m if >0s
                default -> throw new NumberFormatException("Unknown unit: " + unit);
            }
        }
        if (!found) throw new NumberFormatException("Invalid duration: " + input);

        // ensure entire string was consumed (allow whitespace between tokens)
        String stripped = TOKEN.matcher(s).replaceAll("").trim();
        // also remove any remaining whitespace
        stripped = stripped.replaceAll("\\s+", "");
        if (!stripped.isEmpty()) {
            throw new NumberFormatException("Invalid duration: " + input);
        }
        return totalMinutes;
    }
}
