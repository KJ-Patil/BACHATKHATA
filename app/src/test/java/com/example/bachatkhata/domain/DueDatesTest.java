package com.example.bachatkhata.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DueDatesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 24);

    @Test
    public void projectsFutureEmiInstalmentsOnly() {
        // Loan started 6 months ago on the 10th, 24-month tenure.
        DueDates.LoanInput loan = new DueDates.LoanInput(
                "Car Loan", 15000, 24, LocalDate.of(2026, 1, 10));

        Map<String, List<DueDates.DueItem>> due = DueDates.compute(
                Collections.singletonList(loan), Collections.emptyList(), TODAY);

        // The Jan–Jul instalments are past; none should appear before today.
        for (List<DueDates.DueItem> items : due.values()) {
            for (DueDates.DueItem item : items) {
                assertFalse("past instalment leaked in", item.date.isBefore(TODAY));
                assertEquals(DueDates.KIND_EMI, item.kind);
                assertEquals(15000, item.amount, 0.001);
            }
        }
        // August's instalment (10th) is the next one and must be present.
        assertTrue(due.containsKey("2026-08-10"));
    }

    @Test
    public void doesNotProjectBeyondTenure() {
        // 2-month tenure starting this month: only a couple of instalments ever.
        DueDates.LoanInput loan = new DueDates.LoanInput(
                "Short Loan", 5000, 2, LocalDate.of(2026, 7, 5));

        Map<String, List<DueDates.DueItem>> due = DueDates.compute(
                Collections.singletonList(loan), Collections.emptyList(), TODAY);

        int count = 0;
        for (List<DueDates.DueItem> items : due.values()) count += items.size();
        // Instalments on Aug 5 and Sep 5 (Jul 5 is past). No more after tenure.
        assertEquals(2, count);
    }

    @Test
    public void projectsMonthlySubscriptionsAcrossHorizon() {
        DueDates.SubscriptionInput sub = new DueDates.SubscriptionInput(
                "Streaming", 199, LocalDate.of(2026, 8, 1));

        Map<String, List<DueDates.DueItem>> due = DueDates.compute(
                Collections.emptyList(), Collections.singletonList(sub), TODAY);

        int count = 0;
        for (List<DueDates.DueItem> items : due.values()) count += items.size();
        // Roughly one per month across a 12-month horizon.
        assertTrue("expected ~12 monthly charges, got " + count, count >= 11 && count <= 13);
        assertTrue(due.containsKey("2026-08-01"));
        assertTrue(due.containsKey("2026-09-01"));
    }

    @Test
    public void fastForwardsALapsedSubscriptionAnchor() {
        // Anchor is months in the past; projection must start from today's side,
        // not dump a wall of historical charges.
        DueDates.SubscriptionInput sub = new DueDates.SubscriptionInput(
                "Old Sub", 99, LocalDate.of(2025, 1, 15));

        Map<String, List<DueDates.DueItem>> due = DueDates.compute(
                Collections.emptyList(), Collections.singletonList(sub), TODAY);

        for (List<DueDates.DueItem> items : due.values()) {
            for (DueDates.DueItem item : items) {
                assertFalse(item.date.isBefore(TODAY));
            }
        }
        assertFalse(due.isEmpty());
    }

    @Test
    public void handlesNullAndEmptyInput() {
        assertTrue(DueDates.compute(null, null, TODAY).isEmpty());
        assertTrue(DueDates.compute(Collections.emptyList(), Collections.emptyList(), TODAY).isEmpty());
    }

    @Test
    public void keepsMonthEndAnchorFromDrifting() {
        // Anchored on the 31st: Feb has no 31st, but the payment must return to the
        // 31st afterwards rather than sticking on the 28th.
        DueDates.LoanInput loan = new DueDates.LoanInput(
                "EOM Loan", 1000, 24, LocalDate.of(2026, 1, 31));

        Map<String, List<DueDates.DueItem>> due = DueDates.compute(
                Collections.singletonList(loan), Collections.emptyList(), TODAY);

        assertTrue(due.containsKey("2026-08-31")); // 31-day month keeps the 31st
    }

    @Test
    public void keepsMonthEndSubscriptionAnchorFromDrifting() {
        // Same rule for subscriptions. Stepping occurrence-to-occurrence would clamp
        // the 31st to 28 Feb and then keep the 28th for good; re-deriving from the
        // anchor clamps only in the short month.
        DueDates.SubscriptionInput sub = new DueDates.SubscriptionInput(
                "EOM Sub", 499, LocalDate.of(2026, 8, 31));

        Map<String, List<DueDates.DueItem>> due = DueDates.compute(
                Collections.emptyList(), Collections.singletonList(sub), TODAY);

        assertTrue("August charge missing", due.containsKey("2026-08-31"));
        assertTrue("short month must clamp", due.containsKey("2026-09-30"));
        assertTrue("October must return to the 31st", due.containsKey("2026-10-31"));
        assertTrue("February clamps to the 28th", due.containsKey("2027-02-28"));
        assertTrue("March must return to the 31st, not stay on the 28th",
                due.containsKey("2027-03-31"));
    }
}
