package com.example.bachatkhata;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieEntry;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class AnalyticsViewModel extends ViewModel {

    private final MutableLiveData<List<PieEntry>> pieChartData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<CategoryBreakdown>> categoryBreakdowns = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<BarEntry>> barChartData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<String>> barChartLabels = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> selectedType = new MutableLiveData<>("expense"); // "expense" or "income"

    private final List<Transaction> allTransactions = new ArrayList<>();

    public LiveData<List<PieEntry>> getPieChartData() {
        return pieChartData;
    }

    public LiveData<List<CategoryBreakdown>> getCategoryBreakdowns() {
        return categoryBreakdowns;
    }

    public LiveData<List<BarEntry>> getBarChartData() {
        return barChartData;
    }

    public LiveData<List<String>> getBarChartLabels() {
        return barChartLabels;
    }

    public LiveData<String> getSelectedType() {
        return selectedType;
    }

    public void init(String uid) {
        // Observe transactions in real time
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

    private void calculateAnalytics() {
        String type = selectedType.getValue();
        if (type == null) type = "expense";

        // 1. Process Pie Chart Data and Category Breakdowns
        double totalAmountSum = 0;
        Map<String, Double> categorySums = new HashMap<>();

        for (Transaction t : allTransactions) {
            if (type.equalsIgnoreCase(t.getType())) {
                double amt = t.getAmount();
                totalAmountSum += amt;
                String cat = t.getCategory();
                if (cat != null) {
                    categorySums.put(cat, categorySums.getOrDefault(cat, 0.0) + amt);
                }
            }
        }

        List<PieEntry> pieEntries = new ArrayList<>();
        List<CategoryBreakdown> breakdowns = new ArrayList<>();

        for (Map.Entry<String, Double> entry : categorySums.entrySet()) {
            double pct = totalAmountSum > 0 ? (entry.getValue() / totalAmountSum) * 100 : 0;
            // PieEntry takes percentage/value and label
            pieEntries.add(new PieEntry((float) pct, entry.getKey()));
            breakdowns.add(new CategoryBreakdown(entry.getKey(), entry.getValue(), pct));
        }

        // Sort breakdowns from highest spending/earning to lowest
        Collections.sort(breakdowns, (b1, b2) -> Double.compare(b2.getAmount(), b1.getAmount()));

        pieChartData.setValue(pieEntries);
        categoryBreakdowns.setValue(breakdowns);

        // 2. Process Monthly Net Savings (Bar Chart) for the last 6 months
        buildMonthlyComparisonData();
    }

    private void buildMonthlyComparisonData() {
        // We will compute Net Cash Flow (Income - Expense) for each of the last 6 months
        Calendar cal = Calendar.getInstance();
        
        // Let's create an ordered map of month keys (e.g., "2026-01" -> Net Value)
        // LinkedHashMap or TreeMap to maintain order
        Map<String, MonthlyFlow> monthlyFlowMap = new TreeMap<>();

        // Initialize last 6 months in the map with 0 values
        for (int i = 5; i >= 0; i--) {
            Calendar tempCal = Calendar.getInstance();
            tempCal.add(Calendar.MONTH, -i);
            String yearMonthKey = String.format(Locale.US, "%d-%02d", tempCal.get(Calendar.YEAR), tempCal.get(Calendar.MONTH) + 1);
            String displayLabel = tempCal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.US);
            monthlyFlowMap.put(yearMonthKey, new MonthlyFlow(displayLabel));
        }

        // Accumulate transactions into months
        for (Transaction t : allTransactions) {
            if (t.getDate() != null) {
                cal.setTime(t.getDate());
                String key = String.format(Locale.US, "%d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1);
                
                if (monthlyFlowMap.containsKey(key)) {
                    MonthlyFlow flow = monthlyFlowMap.get(key);
                    if (flow != null) {
                        if ("income".equalsIgnoreCase(t.getType())) {
                            flow.income += t.getAmount();
                        } else if ("expense".equalsIgnoreCase(t.getType())) {
                            flow.expense += t.getAmount();
                        }
                    }
                }
            }
        }

        List<BarEntry> barEntries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int index = 0;

        for (Map.Entry<String, MonthlyFlow> entry : monthlyFlowMap.entrySet()) {
            MonthlyFlow flow = entry.getValue();
            double netSavings = flow.income - flow.expense;
            barEntries.add(new BarEntry(index++, (float) netSavings));
            labels.add(flow.label);
        }

        barChartData.setValue(barEntries);
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

        public String getCategoryName() {
            return categoryName;
        }

        public double getAmount() {
            return amount;
        }

        public double getPercentage() {
            return percentage;
        }
    }

    private static class MonthlyFlow {
        final String label;
        double income = 0;
        double expense = 0;

        MonthlyFlow(String label) {
            this.label = label;
        }
    }
}
