package com.example.bachatkhata.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Logging streaks and achievement badges, computed from the real ledger.
 *
 * <p>Port of {@code computeStreaksAndBadges} (ANDROID_FEATURES.md §5.9). Pure Java —
 * no Firestore, no Android — so it can be unit-tested and so every caller derives
 * the same numbers from the same rows.
 *
 * <p>Badges are <em>derived</em>, never stored. A stored award cannot be shown with
 * progress ("60 / 100 transactions"), drifts out of step when data is deleted, and
 * has to be backfilled for anyone who earned it before the badge existed. Recomputing
 * costs one pass over the transactions the screen already loaded.
 */
public final class Streaks {

    private Streaks() {
    }

    /** One transaction, reduced to the fields the streak/badge maths needs. */
    public static final class Entry {
        public final LocalDate date;
        public final double amount;
        public final boolean income;
        public final String category;

        public Entry(LocalDate date, double amount, boolean income, String category) {
            this.date = date;
            this.amount = amount;
            this.income = income;
            this.category = category;
        }
    }

    /** A single achievement, with enough progress detail to render a locked state. */
    public static final class Badge {
        public final String id;
        public final String name;
        public final String description;
        /** Progress toward {@link #target}, clamped to the target once earned. */
        public final long progress;
        public final long target;
        public final boolean earned;

        Badge(String id, String name, String description, long progress, long target) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.target = target;
            this.earned = progress >= target;
            this.progress = Math.min(progress, target);
        }

        /** Percentage complete, 0–100. */
        public int percent() {
            if (target <= 0) return earned ? 100 : 0;
            return (int) Math.min(100, Math.max(0, (progress * 100) / target));
        }
    }

    public static final class Result {
        public final int currentStreak;
        public final int longestStreak;
        public final int activeDays;
        public final List<Badge> badges;
        public final int earnedCount;

        Result(int currentStreak, int longestStreak, int activeDays, List<Badge> badges) {
            this.currentStreak = currentStreak;
            this.longestStreak = longestStreak;
            this.activeDays = activeDays;
            this.badges = badges;
            int earned = 0;
            for (Badge b : badges) if (b.earned) earned++;
            this.earnedCount = earned;
        }
    }

    /**
     * Computes streaks and the full badge set.
     *
     * @param entries  every transaction the user has logged
     * @param budgets  category → monthly limit; drives the Budget Boss badge
     * @param goalProgress one entry per savings goal, as saved ÷ target
     * @param today    the reference day, injected so this stays testable
     */
    public static Result compute(List<Entry> entries,
                                 Map<String, Double> budgets,
                                 List<Double> goalProgress,
                                 LocalDate today) {
        if (entries == null) entries = Collections.emptyList();
        if (goalProgress == null) goalProgress = Collections.emptyList();

        // Distinct days with at least one entry, ascending.
        TreeSet<LocalDate> days = new TreeSet<>();
        for (Entry e : entries) {
            if (e != null && e.date != null) days.add(e.date);
        }

        int longest = longestRun(days);
        int current = currentRun(days, today);

        int totalCount = entries.size();
        double savingsRate = savingsRate(entries, today);
        Set<String> categories = new HashSet<>();
        for (Entry e : entries) {
            if (e != null && !e.income && e.category != null && !e.category.trim().isEmpty()) {
                categories.add(e.category.trim().toLowerCase(java.util.Locale.US));
            }
        }
        boolean budgetBoss = withinAllBudgets(entries, budgets, today);
        int goalsCompleted = 0;
        for (Double p : goalProgress) {
            if (p != null && p >= 1.0) goalsCompleted++;
        }

        List<Badge> badges = new ArrayList<>();
        badges.add(new Badge("first_transaction", "First Step",
                "Log your first transaction", totalCount, 1));
        badges.add(new Badge("streak_3", "Getting Consistent",
                "Log something 3 days in a row", longest, 3));
        badges.add(new Badge("streak_7", "On Fire",
                "Keep a 7-day logging streak", longest, 7));
        badges.add(new Badge("streak_30", "Unstoppable",
                "Keep a 30-day logging streak", longest, 30));
        badges.add(new Badge("saver_20", "Smart Saver",
                "Save 20% of this month's income", Math.round(savingsRate * 100), 20));
        badges.add(new Badge("saver_40", "Super Saver",
                "Save 40% of this month's income", Math.round(savingsRate * 100), 40));
        badges.add(new Badge("budget_champion", "Budget Boss",
                "Stay inside every category budget this month", budgetBoss ? 1 : 0, 1));
        badges.add(new Badge("goal_crusher", "Goal Crusher",
                "Reach a savings goal", goalsCompleted, 1));
        badges.add(new Badge("well_rounded", "Well Rounded",
                "Spend across 5 different categories", categories.size(), 5));
        badges.add(new Badge("centurion", "Centurion",
                "Log 100 transactions", totalCount, 100));

        return new Result(current, longest, days.size(), badges);
    }

    /** Longest run of consecutive days anywhere in the history. */
    private static int longestRun(TreeSet<LocalDate> days) {
        int longest = 0;
        int run = 0;
        LocalDate previous = null;
        for (LocalDate day : days) {
            if (previous != null && previous.plusDays(1).equals(day)) {
                run++;
            } else {
                run = 1;
            }
            if (run > longest) longest = run;
            previous = day;
        }
        return longest;
    }

    /**
     * The run ending at today or yesterday. A streak that stopped three days ago is
     * not "current" — counting it would tell the user they are still on a streak
     * they have already broken, which is the one thing this number must not do.
     * Yesterday still counts, so the streak does not appear broken simply because
     * today's transaction has not been entered yet.
     */
    private static int currentRun(TreeSet<LocalDate> days, LocalDate today) {
        if (days.isEmpty()) return 0;
        LocalDate last = days.last();
        if (!last.equals(today) && !last.equals(today.minusDays(1))) return 0;

        int run = 0;
        LocalDate cursor = last;
        while (days.contains(cursor)) {
            run++;
            cursor = cursor.minusDays(1);
        }
        return run;
    }

    /** (income − expense) ÷ income for the current calendar month; 0 when no income. */
    private static double savingsRate(List<Entry> entries, LocalDate today) {
        double income = 0;
        double expense = 0;
        for (Entry e : entries) {
            if (e == null || e.date == null) continue;
            if (e.date.getYear() != today.getYear() || e.date.getMonth() != today.getMonth()) continue;
            if (e.income) income += e.amount;
            else expense += e.amount;
        }
        if (income <= 0) return 0;
        return Math.max(0, (income - expense) / income);
    }

    /**
     * True when every configured budget is still unbroken this month. Requires at
     * least one budget — with none configured there is nothing to stay inside, and
     * awarding the badge for that would make it meaningless.
     */
    private static boolean withinAllBudgets(List<Entry> entries, Map<String, Double> budgets,
                                            LocalDate today) {
        if (budgets == null || budgets.isEmpty()) return false;

        java.util.Map<String, Double> spentByCategory = new java.util.HashMap<>();
        for (Entry e : entries) {
            if (e == null || e.income || e.date == null || e.category == null) continue;
            if (e.date.getYear() != today.getYear() || e.date.getMonth() != today.getMonth()) continue;
            String key = e.category.trim().toLowerCase(java.util.Locale.US);
            spentByCategory.merge(key, e.amount, Double::sum);
        }

        for (Map.Entry<String, Double> budget : budgets.entrySet()) {
            if (budget.getKey() == null || budget.getValue() == null || budget.getValue() <= 0) continue;
            double spent = spentByCategory.getOrDefault(
                    budget.getKey().trim().toLowerCase(java.util.Locale.US), 0d);
            if (spent > budget.getValue()) return false;
        }
        return true;
    }
}
