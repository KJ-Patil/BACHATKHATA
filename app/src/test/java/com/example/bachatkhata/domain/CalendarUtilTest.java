package com.example.bachatkhata.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.util.List;

public class CalendarUtilTest {

    @Test
    public void dateKeyRoundTrips() {
        LocalDate date = LocalDate.of(2026, 7, 24);
        assertEquals("2026-07-24", CalendarUtil.toDateKey(date));
        assertEquals(date, CalendarUtil.parseDateKey("2026-07-24"));
    }

    @Test
    public void parseDateKeyRejectsMalformed() {
        assertNull(CalendarUtil.parseDateKey(null));
        assertNull(CalendarUtil.parseDateKey(""));
        assertNull(CalendarUtil.parseDateKey("2026-13-40"));
        assertNull(CalendarUtil.parseDateKey("not-a-date"));
        assertNull(CalendarUtil.parseDateKey("24/07/2026"));
    }

    @Test
    public void gridHas42CellsAndIsMondayFirst() {
        // July 2026: the 1st is a Wednesday, so the grid should start on Monday
        // June 29 (two leading days).
        List<LocalDate> grid = CalendarUtil.buildMonthGrid(2026, 7);
        assertEquals(42, grid.size());
        assertEquals(LocalDate.of(2026, 6, 29), grid.get(0));   // Monday
        assertEquals(java.time.DayOfWeek.MONDAY, grid.get(0).getDayOfWeek());
        assertEquals(LocalDate.of(2026, 7, 1), grid.get(2));    // Wednesday the 1st
    }

    @Test
    public void gridWhereFirstIsMondayHasNoLeadingSpill() {
        // June 2026: the 1st is a Monday, so cell 0 is exactly the 1st.
        List<LocalDate> grid = CalendarUtil.buildMonthGrid(2026, 6);
        assertEquals(LocalDate.of(2026, 6, 1), grid.get(0));
    }

    @Test
    public void gridWhereFirstIsSundayHasSixLeadingCells() {
        // November 2026: the 1st is a Sunday; Monday-first means six leading cells.
        List<LocalDate> grid = CalendarUtil.buildMonthGrid(2026, 11);
        assertEquals(LocalDate.of(2026, 10, 26), grid.get(0)); // Monday
        assertEquals(LocalDate.of(2026, 11, 1), grid.get(6));  // Sunday the 1st
    }

    @Test
    public void isSameDay() {
        assertTrue(CalendarUtil.isSameDay(LocalDate.of(2026, 7, 24), LocalDate.of(2026, 7, 24)));
        assertFalse(CalendarUtil.isSameDay(LocalDate.of(2026, 7, 24), LocalDate.of(2026, 7, 25)));
        assertFalse(CalendarUtil.isSameDay(null, LocalDate.of(2026, 7, 24)));
    }
}
