package com.example.bachatkhata;

import com.google.firebase.Timestamp;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * The "anti-budget" single number — how much can safely be spent per day for the
 * rest of the current month. Mirrors the web app's {@code computeSafeToSpend}.
 *
 * pool = this month's income (falls back to total budget if no income yet)
 *        − this month's expenses
 *        − savings reserve (Σ goal-remaining ÷ months-to-deadline)
 * safePerDay = max(0, pool) ÷ days left in the month (today inclusive).
 */
public class SafeToSpendCalculator {

    public static class Result {
        public final double safePerDay;
        public final double poolRemaining;
        public final int daysLeft;
        public final double monthlyReserve;
        public final boolean usedBudgetFallback;

        Result(double safePerDay, double poolRemaining, int daysLeft,
               double monthlyReserve, boolean usedBudgetFallback) {
            this.safePerDay = safePerDay;
            this.poolRemaining = poolRemaining;
            this.daysLeft = daysLeft;
            this.monthlyReserve = monthlyReserve;
            this.usedBudgetFallback = usedBudgetFallback;
        }
    }

    public static Result compute(List<Transaction> txns, List<Budget> budgets, List<SavingsGoal> goals) {
        Calendar now = Calendar.getInstance();
        int curMonth = now.get(Calendar.MONTH);
        int curYear = now.get(Calendar.YEAR);

        double monthIncome = 0.0;
        double monthExpense = 0.0;
        if (txns != null) {
            for (Transaction t : txns) {
                if (t == null || t.getDate() == null) continue;
                Calendar c = Calendar.getInstance();
                c.setTime(t.getDate());
                if (c.get(Calendar.MONTH) != curMonth || c.get(Calendar.YEAR) != curYear) continue;
                if ("income".equalsIgnoreCase(t.getType())) {
                    monthIncome += t.getAmount();
                } else if ("expense".equalsIgnoreCase(t.getType())) {
                    monthExpense += t.getAmount();
                }
            }
        }

        // Income drives the pool; if there's no income yet this month, fall back to
        // the sum of the user's monthly budget limits as the spending ceiling.
        boolean usedBudgetFallback = false;
        double ceiling = monthIncome;
        if (ceiling <= 0.0) {
            double totalBudget = 0.0;
            if (budgets != null) {
                for (Budget b : budgets) {
                    if (b != null) totalBudget += b.getLimitAmount();
                }
            }
            ceiling = totalBudget;
            usedBudgetFallback = totalBudget > 0.0;
        }

        double monthlyReserve = monthlySavingsReserve(goals, now);

        double pool = ceiling - monthExpense - monthlyReserve;
        if (pool < 0.0) pool = 0.0;

        int daysLeft = daysLeftInMonth(now);
        double safePerDay = daysLeft > 0 ? pool / daysLeft : pool;

        return new Result(safePerDay, pool, daysLeft, monthlyReserve, usedBudgetFallback);
    }

    /** Days remaining in the current month, including today. */
    private static int daysLeftInMonth(Calendar now) {
        int today = now.get(Calendar.DAY_OF_MONTH);
        int lastDay = now.getActualMaximum(Calendar.DAY_OF_MONTH);
        return Math.max(1, lastDay - today + 1);
    }

    /** Σ over goals of (remaining ÷ months until deadline). */
    private static double monthlySavingsReserve(List<SavingsGoal> goals, Calendar now) {
        if (goals == null) return 0.0;
        double reserve = 0.0;
        for (SavingsGoal g : goals) {
            if (g == null) continue;
            double remaining = g.getTargetAmount() - g.getSavedAmount();
            if (remaining <= 0.0) continue;

            int monthsToDeadline = monthsUntil(g.getDeadline(), now);
            reserve += remaining / monthsToDeadline;
        }
        return reserve;
    }

    /** Whole months from now until the deadline, floored at 1. */
    private static int monthsUntil(Timestamp deadline, Calendar now) {
        if (deadline == null) return 12; // no deadline → spread over a year
        Date deadlineDate = deadline.toDate();
        if (!deadlineDate.after(now.getTime())) return 1; // overdue/this month → reserve it now

        Calendar d = Calendar.getInstance();
        d.setTime(deadlineDate);
        int months = (d.get(Calendar.YEAR) - now.get(Calendar.YEAR)) * 12
                + (d.get(Calendar.MONTH) - now.get(Calendar.MONTH));
        return Math.max(1, months);
    }
}
