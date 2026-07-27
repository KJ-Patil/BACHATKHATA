package com.example.bachatkhata.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only projection of upcoming obligations onto calendar days. Nothing is
 * persisted — it's recomputed from live loans and subscriptions.
 *
 * <p>Pure Java, unit-testable. Callers convert their Firestore rows into the small
 * input records below.
 */
public final class DueDates {

    /** How far ahead to project, in months. */
    private static final int HORIZON_MONTHS = 12;

    private DueDates() {
    }

    public static final String KIND_EMI = "emi";
    public static final String KIND_SUBSCRIPTION = "subscription";

    /** A projected obligation on a given day. */
    public static final class DueItem {
        public final String kind;   // KIND_EMI | KIND_SUBSCRIPTION
        public final String label;
        public final double amount;
        public final LocalDate date;

        public DueItem(String kind, String label, double amount, LocalDate date) {
            this.kind = kind;
            this.label = label;
            this.amount = amount;
            this.date = date;
        }
    }

    /** A loan/EMI to project: fixed monthly payment from {@code startDate}. */
    public static final class LoanInput {
        public final String name;
        public final double emiAmount;
        public final int tenureMonths;
        public final LocalDate startDate;

        public LoanInput(String name, double emiAmount, int tenureMonths, LocalDate startDate) {
            this.name = name;
            this.emiAmount = emiAmount;
            this.tenureMonths = tenureMonths;
            this.startDate = startDate;
        }
    }

    /** A monthly subscription to project, anchored to its next renewal date. */
    public static final class SubscriptionInput {
        public final String name;
        public final double amount;
        public final LocalDate nextRenewal;

        public SubscriptionInput(String name, double amount, LocalDate nextRenewal) {
            this.name = name;
            this.amount = amount;
            this.nextRenewal = nextRenewal;
        }
    }

    /**
     * Projects EMIs and subscriptions onto day keys, from {@code today} up to
     * {@value #HORIZON_MONTHS} months ahead.
     *
     * @return day key ({@code "yyyy-MM-dd"}) → items due that day
     */
    public static Map<String, List<DueItem>> compute(List<LoanInput> loans,
                                                     List<SubscriptionInput> subscriptions,
                                                     LocalDate today) {
        Map<String, List<DueItem>> byDay = new HashMap<>();
        LocalDate horizon = today.plusMonths(HORIZON_MONTHS);

        if (loans != null) {
            for (LoanInput loan : loans) {
                projectLoan(loan, today, horizon, byDay);
            }
        }
        if (subscriptions != null) {
            for (SubscriptionInput sub : subscriptions) {
                projectSubscription(sub, today, horizon, byDay);
            }
        }
        return byDay;
    }

    private static void projectLoan(LoanInput loan, LocalDate today, LocalDate horizon,
                                    Map<String, List<DueItem>> byDay) {
        if (loan == null || loan.startDate == null || loan.tenureMonths <= 0) return;

        // Each instalment falls on the same day-of-month as the start date, one per
        // month for the loan's tenure. Only future, not-yet-past instalments inside
        // the horizon are projected.
        for (int i = 1; i <= loan.tenureMonths; i++) {
            LocalDate due = addMonthsClamped(loan.startDate, i);
            if (due.isBefore(today)) continue;
            if (due.isAfter(horizon)) break;
            add(byDay, new DueItem(KIND_EMI, loan.name, loan.emiAmount, due));
        }
    }

    private static void projectSubscription(SubscriptionInput sub, LocalDate today, LocalDate horizon,
                                            Map<String, List<DueItem>> byDay) {
        if (sub == null || sub.nextRenewal == null) return;

        // Walk monthly from the next renewal. If that date is already in the past
        // (a lapsed anchor), fast-forward to the first occurrence >= today so we
        // don't emit a wall of historical charges.
        LocalDate due = sub.nextRenewal;
        int guard = 0;
        while (due.isBefore(today) && guard < 600) {
            due = addMonthsClamped(due, 1);
            guard++;
        }
        while (!due.isAfter(horizon)) {
            add(byDay, new DueItem(KIND_SUBSCRIPTION, sub.name, sub.amount, due));
            due = addMonthsClamped(due, 1);
        }
    }

    /**
     * Adds months while keeping the anchor day-of-month where possible — a payment
     * anchored on the 31st lands on the 30th/28th in shorter months, then returns
     * to 31 rather than drifting earlier each time.
     */
    private static LocalDate addMonthsClamped(LocalDate anchor, int months) {
        LocalDate shifted = anchor.plusMonths(months);
        int targetDay = Math.min(anchor.getDayOfMonth(), shifted.lengthOfMonth());
        return shifted.withDayOfMonth(targetDay);
    }

    private static void add(Map<String, List<DueItem>> byDay, DueItem item) {
        String key = CalendarUtil.toDateKey(item.date);
        byDay.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
    }
}
