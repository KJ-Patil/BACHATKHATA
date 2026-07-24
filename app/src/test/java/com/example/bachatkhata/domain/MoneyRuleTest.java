package com.example.bachatkhata.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bachatkhata.Category;
import com.example.bachatkhata.Transaction;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MoneyRuleTest {

    private static final int YEAR = 2026;
    private static final int JULY = Calendar.JULY; // 6, 0-based

    private static final double EPS = 0.001;

    private static Date dateOf(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(year, month, day, 12, 0, 0);
        return cal.getTime();
    }

    private static Transaction expense(double amount, String category, Date date) {
        return txn(amount, "expense", category, date);
    }

    private static Transaction txn(double amount, String type, String category, Date date) {
        Transaction t = new Transaction();
        t.setId(category + "-" + amount + "-" + date.getTime());
        t.setAmount(amount);
        t.setType(type);
        t.setCategory(category);
        t.setDate(date);
        return t;
    }

    // ── Split ───────────────────────────────────────────────────────────

    @Test
    public void defaultSplitIs50_30_20() {
        MoneyRule.Split s = MoneyRule.Split.defaultSplit();
        assertEquals(50, s.needs);
        assertEquals(30, s.wants);
        assertEquals(20, s.investments);
        assertTrue(s.isValid());
    }

    @Test
    public void splitMustTotalExactly100() {
        assertTrue(new MoneyRule.Split(60, 20, 20).isValid());
        assertFalse(new MoneyRule.Split(50, 30, 30).isValid()); // 110
        assertFalse(new MoneyRule.Split(50, 30, 10).isValid()); // 90
        assertFalse(new MoneyRule.Split(110, -10, 0).isValid()); // negative
    }

    @Test
    public void invalidSplitFallsBackToDefault() {
        List<Transaction> txs = Collections.singletonList(expense(1000, "Rent", dateOf(YEAR, JULY, 5)));
        MoneyRule.Result result = MoneyRule.compute(
                10000, txs, YEAR, JULY, new MoneyRule.Split(80, 80, 80), null, null);
        // Falls back to 50/30/20 rather than allocating 240% of income.
        assertEquals(5000, result.get(BucketType.NEEDS).budget, EPS);
    }

    // ── Bucketing and month filtering ───────────────────────────────────

    @Test
    public void expensesAreBucketedAndBudgetsDerivedFromIncome() {
        List<Transaction> txs = new ArrayList<>();
        txs.add(expense(8000, "Rent", dateOf(YEAR, JULY, 1)));          // needs
        txs.add(expense(2000, "Food", dateOf(YEAR, JULY, 10)));         // needs
        txs.add(expense(3000, "Entertainment", dateOf(YEAR, JULY, 15)));// wants
        txs.add(expense(5000, "Investment", dateOf(YEAR, JULY, 20)));   // investments

        MoneyRule.Result r = MoneyRule.compute(
                50000, txs, YEAR, JULY, MoneyRule.Split.defaultSplit(), null, null);

        assertEquals(25000, r.get(BucketType.NEEDS).budget, EPS);
        assertEquals(15000, r.get(BucketType.WANTS).budget, EPS);
        assertEquals(10000, r.get(BucketType.INVESTMENTS).budget, EPS);

        assertEquals(10000, r.get(BucketType.NEEDS).spent, EPS);
        assertEquals(3000, r.get(BucketType.WANTS).spent, EPS);
        assertEquals(5000, r.get(BucketType.INVESTMENTS).spent, EPS);

        assertEquals(15000, r.get(BucketType.NEEDS).remaining, EPS);
        assertEquals(18000, r.totalSpent, EPS);
    }

    @Test
    public void incomeTransactionsAreIgnored() {
        List<Transaction> txs = new ArrayList<>();
        txs.add(txn(50000, "income", "Salary", dateOf(YEAR, JULY, 1)));
        txs.add(expense(1000, "Food", dateOf(YEAR, JULY, 2)));

        MoneyRule.Result r = MoneyRule.compute(
                50000, txs, YEAR, JULY, null, null, null);
        assertEquals(1000, r.totalSpent, EPS);
    }

    @Test
    public void onlyTargetMonthCounts() {
        List<Transaction> txs = new ArrayList<>();
        txs.add(expense(1000, "Food", dateOf(YEAR, JULY, 15)));
        txs.add(expense(9999, "Food", dateOf(YEAR, Calendar.JUNE, 15)));  // previous month
        txs.add(expense(8888, "Food", dateOf(YEAR, Calendar.AUGUST, 15))); // next month
        txs.add(expense(7777, "Food", dateOf(YEAR - 1, JULY, 15)));        // same month, prior year

        MoneyRule.Result r = MoneyRule.compute(10000, txs, YEAR, JULY, null, null, null);
        assertEquals(1000, r.get(BucketType.NEEDS).spent, EPS);
    }

    @Test
    public void transactionWithNoDateIsSkipped() {
        Transaction t = new Transaction();
        t.setAmount(500);
        t.setType("expense");
        t.setCategory("Food");
        t.setDate(null);

        MoneyRule.Result r = MoneyRule.compute(
                10000, Collections.singletonList(t), YEAR, JULY, null, null, null);
        assertEquals(0, r.totalSpent, EPS);
    }

    @Test
    public void userBucketChoiceDrivesTheRollup() {
        Category travel = new Category();
        travel.setName("Travel");
        travel.setType("expense");
        travel.setBucket("needs"); // re-tagged from the built-in "wants"

        List<Transaction> txs = Collections.singletonList(
                expense(4000, "Travel", dateOf(YEAR, JULY, 3)));

        MoneyRule.Result r = MoneyRule.compute(20000, txs, YEAR, JULY, null, null,
                Collections.singletonList(travel));

        assertEquals(4000, r.get(BucketType.NEEDS).spent, EPS);
        assertEquals(0, r.get(BucketType.WANTS).spent, EPS);
    }

    // ── Status thresholds ───────────────────────────────────────────────

    @Test
    public void statusOnTrackBelow90Percent() {
        MoneyRule.Result r = MoneyRule.compute(10000,
                Collections.singletonList(expense(4000, "Rent", dateOf(YEAR, JULY, 1))),
                YEAR, JULY, null, null, null);
        // needs budget 5000, spent 4000 => 80%
        assertEquals(80.0, r.get(BucketType.NEEDS).usage, EPS);
        assertEquals(MoneyRule.STATUS_ON_TRACK, r.get(BucketType.NEEDS).status);
    }

    @Test
    public void statusNearLimitAtExactly90Percent() {
        MoneyRule.Result r = MoneyRule.compute(10000,
                Collections.singletonList(expense(4500, "Rent", dateOf(YEAR, JULY, 1))),
                YEAR, JULY, null, null, null);
        assertEquals(90.0, r.get(BucketType.NEEDS).usage, EPS);
        assertEquals(MoneyRule.STATUS_NEAR_LIMIT, r.get(BucketType.NEEDS).status);
    }

    @Test
    public void statusStillNearLimitAtExactly100Percent() {
        MoneyRule.Result r = MoneyRule.compute(10000,
                Collections.singletonList(expense(5000, "Rent", dateOf(YEAR, JULY, 1))),
                YEAR, JULY, null, null, null);
        assertEquals(100.0, r.get(BucketType.NEEDS).usage, EPS);
        // Over Budget means over, not exactly at.
        assertEquals(MoneyRule.STATUS_NEAR_LIMIT, r.get(BucketType.NEEDS).status);
    }

    @Test
    public void statusOverBudgetAbove100Percent() {
        MoneyRule.Result r = MoneyRule.compute(10000,
                Collections.singletonList(expense(5001, "Rent", dateOf(YEAR, JULY, 1))),
                YEAR, JULY, null, null, null);
        assertEquals(MoneyRule.STATUS_OVER_BUDGET, r.get(BucketType.NEEDS).status);
        assertTrue(r.get(BucketType.NEEDS).remaining < 0);
    }

    @Test
    public void zeroIncomeWithSpendIsOverBudget() {
        MoneyRule.Result r = MoneyRule.compute(0,
                Collections.singletonList(expense(500, "Food", dateOf(YEAR, JULY, 1))),
                YEAR, JULY, null, null, null);
        assertEquals(0.0, r.get(BucketType.NEEDS).usage, EPS);
        assertEquals(MoneyRule.STATUS_OVER_BUDGET, r.get(BucketType.NEEDS).status);
    }

    @Test
    public void zeroIncomeAndNoSpendIsOnTrack() {
        MoneyRule.Result r = MoneyRule.compute(0, new ArrayList<>(), YEAR, JULY, null, null, null);
        assertEquals(0.0, r.get(BucketType.WANTS).usage, EPS);
        assertEquals(MoneyRule.STATUS_ON_TRACK, r.get(BucketType.WANTS).status);
    }

    @Test
    public void negativeIncomeIsClampedToZero() {
        MoneyRule.Result r = MoneyRule.compute(-5000, new ArrayList<>(), YEAR, JULY, null, null, null);
        assertEquals(0.0, r.income, EPS);
        assertEquals(0.0, r.get(BucketType.NEEDS).budget, EPS);
    }

    // ── Allocated category budgets ──────────────────────────────────────

    @Test
    public void categoryLimitsRollUpPerBucketAndFlagOverAllocation() {
        Map<String, Double> budgets = new HashMap<>();
        budgets.put("Rent", 20000.0);          // needs
        budgets.put("Food", 8000.0);           // needs  => 28000 vs 25000 limit
        budgets.put("Entertainment", 2000.0);  // wants

        MoneyRule.Result r = MoneyRule.compute(
                50000, new ArrayList<>(), YEAR, JULY, null, budgets, null);

        assertEquals(28000, r.get(BucketType.NEEDS).allocatedBudget, EPS);
        assertEquals(2000, r.get(BucketType.WANTS).allocatedBudget, EPS);
        assertEquals(0, r.get(BucketType.INVESTMENTS).allocatedBudget, EPS);

        assertTrue(r.get(BucketType.NEEDS).isOverAllocated());
        assertFalse(r.get(BucketType.WANTS).isOverAllocated());
    }

    // ── Spent vs Invested ───────────────────────────────────────────────

    @Test
    public void investRateComparesInvestedAgainstTotalOutflow() {
        List<Transaction> txs = new ArrayList<>();
        txs.add(expense(6000, "Rent", dateOf(YEAR, JULY, 1)));        // needs
        txs.add(expense(2000, "Shopping", dateOf(YEAR, JULY, 2)));    // wants
        txs.add(expense(2000, "Investment", dateOf(YEAR, JULY, 3)));  // investments

        MoneyRule.Result r = MoneyRule.compute(20000, txs, YEAR, JULY, null, null, null);

        assertEquals(8000, r.totalConsumed(), EPS);
        assertEquals(2000, r.totalInvested(), EPS);
        assertEquals(20.0, r.investRate(), EPS); // 2000 / 10000
    }

    @Test
    public void investRateIsZeroWithNoOutflow() {
        MoneyRule.Result r = MoneyRule.compute(20000, new ArrayList<>(), YEAR, JULY, null, null, null);
        assertEquals(0.0, r.investRate(), EPS);
    }

    @Test
    public void usageIsRoundedToTwoDecimals() {
        MoneyRule.Result r = MoneyRule.compute(10000,
                Collections.singletonList(expense(1234.5678, "Rent", dateOf(YEAR, JULY, 1))),
                YEAR, JULY, null, null, null);
        // 1234.5678 / 5000 * 100 = 24.691356 -> 24.69
        assertEquals(24.69, r.get(BucketType.NEEDS).usage, EPS);
    }

    @Test
    public void nullTransactionListIsSafe() {
        MoneyRule.Result r = MoneyRule.compute(10000, null, YEAR, JULY, null, null, null);
        assertEquals(0.0, r.totalSpent, EPS);
        assertEquals(5000, r.get(BucketType.NEEDS).budget, EPS);
    }
}
