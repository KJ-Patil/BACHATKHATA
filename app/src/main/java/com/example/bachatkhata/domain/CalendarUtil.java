package com.example.bachatkhata.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Calendar grid + date-key helpers, all in <b>local</b> time so days line up with
 * the rest of the app.
 *
 * <p>Pure Java (java.time, available from API 26). Unit-testable.
 */
public final class CalendarUtil {

    /** Column headers, Monday-first to match the grid. */
    public static final List<String> WEEKDAY_LABELS =
            Arrays.asList("M", "T", "W", "T", "F", "S", "S");

    public static final List<String> MONTH_LABELS = Arrays.asList(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December");

    private static final DateTimeFormatter KEY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private CalendarUtil() {
    }

    /** Grouping key and nav argument for a day: {@code "yyyy-MM-dd"}. */
    public static String toDateKey(LocalDate date) {
        return date.format(KEY_FORMAT);
    }

    /** Parses a {@code "yyyy-MM-dd"} key, returning null on anything malformed. */
    public static LocalDate parseDateKey(String key) {
        if (key == null) return null;
        try {
            return LocalDate.parse(key, KEY_FORMAT);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static boolean isSameDay(LocalDate a, LocalDate b) {
        return a != null && b != null && a.isEqual(b);
    }

    /**
     * Builds a 42-cell (6×7) month grid, <b>Monday-first</b>, including the
     * spill-over days from the previous and next month that fill the first and
     * last rows.
     *
     * @param month 1-based (1 = January), matching {@link #MONTH_LABELS} + 1
     */
    public static List<LocalDate> buildMonthGrid(int year, int month) {
        LocalDate first = LocalDate.of(year, month, 1);

        // java.time's DayOfWeek is already Monday=1..Sunday=7, so the number of
        // leading cells is simply (dayOfWeek - 1). (A Sunday-first source would
        // need (dow + 6) % 7 instead — done once here so the columns never shift.)
        int leading = first.getDayOfWeek().getValue() - 1;

        LocalDate start = first.minusDays(leading);
        List<LocalDate> cells = new ArrayList<>(42);
        for (int i = 0; i < 42; i++) {
            cells.add(start.plusDays(i));
        }
        return cells;
    }

    /** Convenience: today in the device's local zone. */
    public static LocalDate today() {
        return LocalDate.now();
    }

    /** Monday of the week containing {@code date} — matches the grid's alignment. */
    public static DayOfWeek weekStart() {
        return DayOfWeek.MONDAY;
    }
}
