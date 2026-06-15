package com.example.bachatkhata;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieEntry;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class AnalyticsViewModel extends ViewModel {

    private final MutableLiveData<List<PieEntry>> pieChartData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<CategoryBreakdown>> categoryBreakdowns = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> selectedType = new MutableLiveData<>("expense"); // "expense" or "income"
    private final MutableLiveData<String> selectedPeriod = new MutableLiveData<>("This Month"); // "This Month", "Last Month", "Last 3 Months", "This Year"

    // KPIs
    private final MutableLiveData<Double> totalIncome = new MutableLiveData<>(0.0);
    private final MutableLiveData<Double> totalExpense = new MutableLiveData<>(0.0);

    // Line Chart
    private final MutableLiveData<List<Entry>> lineChartIncomeData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Entry>> lineChartExpenseData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<String>> lineChartLabels = new MutableLiveData<>(new ArrayList<>());

    // Grouped Bar Chart (Current vs Previous Month)
    private final MutableLiveData<List<BarEntry>> barChartCurrentData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<BarEntry>> barChartPreviousData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<String>> barChartLabels = new MutableLiveData<>(new ArrayList<>());

    private final List<Transaction> allTransactions = new ArrayList<>();

    public LiveData<List<PieEntry>> getPieChartData() { return pieChartData; }
    public LiveData<List<CategoryBreakdown>> getCategoryBreakdowns() { return categoryBreakdowns; }
    public LiveData<String> getSelectedType() { return selectedType; }
    public LiveData<String> getSelectedPeriod() { return selectedPeriod; }
    public LiveData<Double> getTotalIncome() { return totalIncome; }
    public LiveData<Double> getTotalExpense() { return totalExpense; }
    public LiveData<List<Entry>> getLineChartIncomeData() { return lineChartIncomeData; }
    public LiveData<List<Entry>> getLineChartExpenseData() { return lineChartExpenseData; }
    public LiveData<List<String>> getLineChartLabels() { return lineChartLabels; }
    public LiveData<List<BarEntry>> getBarChartCurrentData() { return barChartCurrentData; }
    public LiveData<List<BarEntry>> getBarChartPreviousData() { return barChartPreviousData; }
    public LiveData<List<String>> getBarChartLabels() { return barChartLabels; }

    public void init(String uid) {
        TransactionRepository.getInstance().observeTransactions(uid, list -> {
            allTransactions.clear();
            allTransactions.addAll(list);
            calculateAnalytics();
        });
    }

    public void setType(String type) {
        selectedType.setValue(type);
        calculateAnalytics();
    }

    public void setPeriod(String period) {
        selectedPeriod.setValue(period);
        calculateAnalytics();
    }

    private void calculateAnalytics() {
        String type = selectedType.getValue();
        if (type == null) type = "expense";
        String period = selectedPeriod.getValue();
        if (period == null) period = "This Month";

        DateRange range = getDateRangeForPeriod(period);

        // 1. Process KPI Totals and Pie Chart (within Selected Period)
        double incomeSum = 0;
        double expenseSum = 0;
        Map<String, Double> categorySums = new HashMap<>();

        for (Transaction t : allTransactions) {
            if (t.getDate() == null) continue;
            Date date = t.getDate();
            if (date.after(range.start) && date.before(range.end)) {
                if ("income".equalsIgnoreCase(t.getType())) {
                    incomeSum += t.getAmount();
                } else if ("expense".equalsIgnoreCase(t.getType())) {
                    expenseSum += t.getAmount();
                }

                if (type.equalsIgnoreCase(t.getType())) {
                    String cat = t.getCategory();
                    if (cat != null) {
                        categorySums.put(cat, categorySums.getOrDefault(cat, 0.0) + t.getAmount());
                    }
                }
            }
        }

        totalIncome.setValue(incomeSum);
        totalExpense.setValue(expenseSum);

        double totalAmountSumForType = "income".equalsIgnoreCase(type) ? incomeSum : expenseSum;

        List<PieEntry> pieEntries = new ArrayList<>();
        List<CategoryBreakdown> breakdowns = new ArrayList<>();

        for (Map.Entry<String, Double> entry : categorySums.entrySet()) {
            double pct = totalAmountSumForType > 0 ? (entry.getValue() / totalAmountSumForType) * 100 : 0;
            pieEntries.add(new PieEntry(entry.getValue().floatValue(), entry.getKey()));
            breakdowns.add(new CategoryBreakdown(entry.getKey(), entry.getValue(), pct));
        }

        // Sort by amount descending
        Collections.sort(breakdowns, (b1, b2) -> Double.compare(b2.getAmount(), b1.getAmount()));

        pieChartData.setValue(pieEntries);
        categoryBreakdowns.setValue(breakdowns);

        // 2. Process Line Chart (Income vs Expense Over Time in Selected Period)
        buildLineChartData(range);

        // 3. Process Grouped Bar Chart (Current Month vs Previous Month per Category)
        buildGroupedBarChartData();
    }

    private DateRange getDateRangeForPeriod(String period) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        Date end = Calendar.getInstance().getTime(); // up to current time
        Date start;

        if ("Last Month".equalsIgnoreCase(period)) {
            cal.add(Calendar.MONTH, -1);
            cal.set(Calendar.DAY_OF_MONTH, 1);
            start = cal.getTime();

            Calendar endCal = Calendar.getInstance();
            endCal.set(Calendar.DAY_OF_MONTH, 1);
            endCal.add(Calendar.DAY_OF_MONTH, -1);
            endCal.set(Calendar.HOUR_OF_DAY, 23);
            endCal.set(Calendar.MINUTE, 59);
            endCal.set(Calendar.SECOND, 59);
            end = endCal.getTime();
        } else if ("Last 3 Months".equalsIgnoreCase(period)) {
            cal.add(Calendar.MONTH, -2);
            cal.set(Calendar.DAY_OF_MONTH, 1);
            start = cal.getTime();
        } else if ("This Year".equalsIgnoreCase(period)) {
            cal.set(Calendar.DAY_OF_YEAR, 1);
            start = cal.getTime();
        } else { // This Month
            cal.set(Calendar.DAY_OF_MONTH, 1);
            start = cal.getTime();
        }

        return new DateRange(start, end);
    }

    private void buildLineChartData(DateRange range) {
        // Daily accumulation
        Map<String, Double> dailyIncome = new TreeMap<>();
        Map<String, Double> dailyExpense = new TreeMap<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat labelFormat = new SimpleDateFormat("dd MMM", Locale.US);

        Calendar c = Calendar.getInstance();
        c.setTime(range.start);
        List<String> dateKeys = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        while (c.getTime().before(range.end) || sdf.format(c.getTime()).equals(sdf.format(range.end))) {
            String key = sdf.format(c.getTime());
            dateKeys.add(key);
            labels.add(labelFormat.format(c.getTime()));
            dailyIncome.put(key, 0.0);
            dailyExpense.put(key, 0.0);
            c.add(Calendar.DAY_OF_MONTH, 1);
        }

        for (Transaction t : allTransactions) {
            if (t.getDate() == null) continue;
            Date date = t.getDate();
            if (date.after(range.start) && (date.before(range.end) || sdf.format(date).equals(sdf.format(range.end)))) {
                String key = sdf.format(date);
                if (dailyIncome.containsKey(key)) {
                    double amt = t.getAmount();
                    if ("income".equalsIgnoreCase(t.getType())) {
                        dailyIncome.put(key, dailyIncome.get(key) + amt);
                    } else if ("expense".equalsIgnoreCase(t.getType())) {
                        dailyExpense.put(key, dailyExpense.get(key) + amt);
                    }
                }
            }
        }

        List<Entry> incomeEntries = new ArrayList<>();
        List<Entry> expenseEntries = new ArrayList<>();

        for (int i = 0; i < dateKeys.size(); i++) {
            String key = dateKeys.get(i);
            incomeEntries.add(new Entry(i, dailyIncome.get(key).floatValue()));
            expenseEntries.add(new Entry(i, dailyExpense.get(key).floatValue()));
        }

        lineChartIncomeData.setValue(incomeEntries);
        lineChartExpenseData.setValue(expenseEntries);
        lineChartLabels.setValue(labels);
    }

    private void buildGroupedBarChartData() {
        // Compares spent/limit per category for current month vs previous month
        Calendar cal = Calendar.getInstance();
        int currentMonth = cal.get(Calendar.MONTH);
        int currentYear = cal.get(Calendar.YEAR);

        cal.add(Calendar.MONTH, -1);
        int prevMonth = cal.get(Calendar.MONTH);
        int prevYear = cal.get(Calendar.YEAR);

        Map<String, Double> currentSpent = new HashMap<>();
        Map<String, Double> previousSpent = new HashMap<>();

        Calendar tCal = Calendar.getInstance();
        for (Transaction t : allTransactions) {
            if (t.getDate() == null || !"expense".equalsIgnoreCase(t.getType())) continue;
            tCal.setTime(t.getDate());
            int m = tCal.get(Calendar.MONTH);
            int y = tCal.get(Calendar.YEAR);
            String cat = t.getCategory();
            if (cat == null) continue;

            if (m == currentMonth && y == currentYear) {
                currentSpent.put(cat, currentSpent.getOrDefault(cat, 0.0) + t.getAmount());
            } else if (m == prevMonth && y == prevYear) {
                previousSpent.put(cat, previousSpent.getOrDefault(cat, 0.0) + t.getAmount());
            }
        }

        // Merge all categories to compare
        List<String> categories = new ArrayList<>(currentSpent.keySet());
        for (String cat : previousSpent.keySet()) {
            if (!categories.contains(cat)) {
                categories.add(cat);
            }
        }

        // Limit to top 5 categories for visual space on grouped bar chart
        if (categories.size() > 5) {
            categories = categories.subList(0, 5);
        }

        List<BarEntry> currentEntries = new ArrayList<>();
        List<BarEntry> previousEntries = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (int i = 0; i < categories.size(); i++) {
            String cat = categories.get(i);
            currentEntries.add(new BarEntry(i, currentSpent.getOrDefault(cat, 0.0).floatValue()));
            previousEntries.add(new BarEntry(i, previousSpent.getOrDefault(cat, 0.0).floatValue()));
            labels.add(cat);
        }

        barChartCurrentData.setValue(currentEntries);
        barChartPreviousData.setValue(previousEntries);
        barChartLabels.setValue(labels);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        TransactionRepository.getInstance().removeListener();
    }

    public static class CategoryBreakdown {
        private final String categoryName;
        private final double amount;
        private final double percentage;

        public CategoryBreakdown(String categoryName, double amount, double percentage) {
            this.categoryName = categoryName;
            this.amount = amount;
            this.percentage = percentage;
        }

        public String getCategoryName() { return categoryName; }
        public double getAmount() { return amount; }
        public double getPercentage() { return percentage; }
    }

    private static class DateRange {
        final Date start;
        final Date end;

        DateRange(Date start, Date end) {
            this.start = start;
            this.end = end;
        }
    }
}
