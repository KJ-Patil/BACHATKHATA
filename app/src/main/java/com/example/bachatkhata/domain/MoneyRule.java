package com.example.bachatkhata.domain;

import com.example.bachatkhata.Category;
import com.example.bachatkhata.Transaction;

import java.util.Calendar;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The Budgeting Rule engine — splits a month's expenses across Needs / Wants / Investments
 * and compares each against its share of income.
 *
 * <p>Pure logic: no Android, no Firestore, no I/O. Everything it needs is passed in.
 */
public final class MoneyRule {

    public static final String STATUS_ON_TRACK = "On Track";
    public static final String STATUS_NEAR_LIMIT = "Near Limit";
    public static final String STATUS_OVER_BUDGET = "Over Budget";

    private MoneyRule() {}

    /**
     * The user-adjustable percentage split. Must total exactly 100 — a split that doesn't
     * would silently under- or over-allocate income, so callers validate before saving.
     */
    public static final class Split {
        public final int needs;
        public final int wants;
        public final int investments;

        public Split(int needs, int wants, int investments) {
            this.needs = needs;
            this.wants = wants;
            this.investments = investments;
        }

        public static Split defaultSplit() {
            return new Split(50, 30, 20);
        }

        public boolean isValid() {
            return needs >= 0 && wants >= 0 && investments >= 0 && total() == 100;
        }

        public int total() {
            return needs + wants + investments;
        }

        /** This bucket's share as a fraction of income, e.g. 0.5 for 50. */
        public double fractionFor(BucketType bucket) {
            switch (bucket) {
                case NEEDS: return needs / 100.0;
                case WANTS: return wants / 100.0;
                case INVESTMENTS: return investments / 100.0;
                default: return 0.0;
            }
        }
    }

    /** Per-bucket rollup for one month. */
    public static final class BucketSummary {
        /** income × the bucket's split percentage. */
        public final double budget;
        /** This month's expenses that resolved into this bucket. */
        public final double spent;
        /** budget − spent; negative once overspent. */
        public final double remaining;
        /** (spent / budget) × 100, rounded to 2dp. 0 when there is neither budget nor spend. */
        public final double usage;
        public final String status;
        /** Σ of the per-category budget limits whose categories fall in this bucket. */
        public final double allocatedBudget;

        BucketSummary(double budget, double spent, double usage, String status, double allocatedBudget) {
            this.budget = budget;
            this.spent = spent;
            this.remaining = budget - spent;
            this.usage = usage;
            this.status = status;
            this.allocatedBudget = allocatedBudget;
        }

        /** True when per-category limits promise more than the rule allows for this bucket. */
        public boolean isOverAllocated() {
            return allocatedBudget > budget;
        }
    }

    /** All three buckets for the requested month. */
    public static final class Result {
        private final Map<BucketType, BucketSummary> buckets;
        public final double income;
        public final double totalSpent;

        Result(Map<BucketType, BucketSummary> buckets, double income, double totalSpent) {
            this.buckets = buckets;
            this.income = income;
            this.totalSpent = totalSpent;
        }

        public BucketSummary get(BucketType bucket) {
            return buckets.get(bucket);
        }

        /** Needs + Wants — money consumed, as opposed to money put to work. */
        public double totalConsumed() {
            return buckets.get(BucketType.NEEDS).spent + buckets.get(BucketType.WANTS).spent;
        }

        public double totalInvested() {
            return buckets.get(BucketType.INVESTMENTS).spent;
        }

        /** invested ÷ total outflow, as a percentage; 0 when nothing went out. */
        public double investRate() {
            double outflow = totalSpent;
            if (outflow <= 0) return 0.0;
            return round2(totalInvested() / outflow * 100.0);
        }
    }

    /**
     * Computes the rule for one calendar month.
     *
     * @param income       monthly take-home pay; 0 is valid and yields empty budgets
     * @param transactions all transactions — filtered to the target month internally
     * @param year         target year, e.g. 2026
     * @param month        target month, <strong>0-based</strong> to match {@link Calendar}
     * @param split        the user's percentage split; null falls back to 50/30/20
     * @param budgets      category name → monthly limit; may be null
     * @param categories   the user's categories, for bucket resolution; may be null
     */
    public static Result compute(double income,
                                 List<Transaction> transactions,
                                 int year,
                                 int month,
                                 Split split,
                                 Map<String, Double> budgets,
                                 List<Category> categories) {

        Split effectiveSplit = (split != null && split.isValid()) ? split : Split.defaultSplit();
        double safeIncome = Math.max(0, income);

        EnumMap<BucketType, Double> spentPerBucket = new EnumMap<>(BucketType.class);
        EnumMap<BucketType, Double> allocatedPerBucket = new EnumMap<>(BucketType.class);
        for (BucketType b : BucketType.values()) {
            spentPerBucket.put(b, 0.0);
            allocatedPerBucket.put(b, 0.0);
        }

        double totalSpent = 0.0;
        if (transactions != null) {
            Calendar cal = Calendar.getInstance();
            for (Transaction t : transactions) {
                if (t == null || !"expense".equalsIgnoreCase(t.getType())) continue;
                if (!isInMonth(cal, t.getDate(), year, month)) continue;

                BucketType bucket = Categories.resolveBucketForCategory(t.getCategory(), categories);
                spentPerBucket.put(bucket, spentPerBucket.get(bucket) + t.getAmount());
                totalSpent += t.getAmount();
            }
        }

        if (budgets != null) {
            for (Map.Entry<String, Double> entry : budgets.entrySet()) {
                if (entry.getValue() == null) continue;
                BucketType bucket = Categories.resolveBucketForCategory(entry.getKey(), categories);
                allocatedPerBucket.put(bucket, allocatedPerBucket.get(bucket) + entry.getValue());
            }
        }

        EnumMap<BucketType, BucketSummary> summaries = new EnumMap<>(BucketType.class);
        for (BucketType b : BucketType.values()) {
            double bucketBudget = safeIncome * effectiveSplit.fractionFor(b);
            double spent = spentPerBucket.get(b);
            double usage = usageFor(bucketBudget, spent);
            summaries.put(b, new BucketSummary(
                    bucketBudget, spent, usage, statusFor(bucketBudget, spent, usage),
                    allocatedPerBucket.get(b)));
        }

        return new Result(summaries, safeIncome, totalSpent);
    }

    private static double usageFor(double budget, double spent) {
        if (budget <= 0) return 0.0;
        return round2(spent / budget * 100.0);
    }

    private static String statusFor(double budget, double spent, double usage) {
        // No budget but money went out: there is nothing to be on track against.
        if (budget <= 0) return spent > 0 ? STATUS_OVER_BUDGET : STATUS_ON_TRACK;
        if (usage > 100.0) return STATUS_OVER_BUDGET;
        if (usage >= 90.0) return STATUS_NEAR_LIMIT;
        return STATUS_ON_TRACK;
    }

    private static boolean isInMonth(Calendar cal, Date date, int year, int month) {
        if (date == null) return false;
        cal.setTime(date);
        return cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month;
    }

    static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
