package com.example.bachatkhata;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class HomeViewModel extends ViewModel {

    private final MutableLiveData<Double> totalIncome = new MutableLiveData<>(0.0);
    private final MutableLiveData<Double> totalSpent = new MutableLiveData<>(0.0);
    private final MutableLiveData<Double> totalBalance = new MutableLiveData<>(0.0);
    private final MutableLiveData<Double> totalSaved = new MutableLiveData<>(0.0);
    private final MutableLiveData<List<Transaction>> recentTransactions = new MutableLiveData<>(new ArrayList<>());
    
    private final MutableLiveData<List<Entry>> lineChartData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<BarEntry>> barChartData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<String>> barChartLabels = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> selectedPeriod = new MutableLiveData<>("Monthly");

    private final FirebaseFirestore mFirestore = FirebaseFirestore.getInstance();
    private ListenerRegistration savingsListener;
    private final List<Transaction> allTransactionsList = new ArrayList<>();

    public LiveData<Double> getTotalIncome() { return totalIncome; }
    public LiveData<Double> getTotalSpent() { return totalSpent; }
    public LiveData<Double> getTotalBalance() { return totalBalance; }
    public LiveData<Double> getTotalSaved() { return totalSaved; }
    public LiveData<List<Transaction>> getRecentTransactions() { return recentTransactions; }
    public LiveData<List<Entry>> getLineChartData() { return lineChartData; }
    public LiveData<List<BarEntry>> getBarChartData() { return barChartData; }
    public LiveData<List<String>> getBarChartLabels() { return barChartLabels; }
    public LiveData<String> getSelectedPeriod() { return selectedPeriod; }

    public void loadDashboardData(String uid, String period) {
        selectedPeriod.setValue(period);
        
        // 1. Observe Transactions in real time
        TransactionRepository.getInstance().observeTransactions(uid, list -> {
            allTransactionsList.clear();
            allTransactionsList.addAll(list);
            processAndEmitData(period);
        });

        // 2. Observe Savings Goals in real time
        if (savingsListener != null) {
            savingsListener.remove();
        }
        savingsListener = mFirestore.collection("users")
                .document(uid)
                .collection("savings_goals")
                .addSnapshotListener((value, error) -> {
                    double savedSum = 0;
                    if (value != null) {
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            Double amt = doc.getDouble("savedAmount");
                            if (amt != null) {
                                savedSum += amt;
                            }
                        }
                    }
                    totalSaved.setValue(savedSum);
                });
    }

    public void changePeriod(String period) {
        selectedPeriod.setValue(period);
        processAndEmitData(period);
    }

    private void processAndEmitData(String period) {
        List<Transaction> filtered = filterByPeriod(allTransactionsList, period);
        calculateKPIs(filtered);
        
        // Emit top 3 transactions for recent list
        List<Transaction> recent = new ArrayList<>();
        for (int i = 0; i < Math.min(3, filtered.size()); i++) {
            recent.add(filtered.get(i));
        }
        recentTransactions.setValue(recent);

        // Build charts
        buildLineChartData(filtered);
        buildBarChartData(filtered);
    }

    private List<Transaction> filterByPeriod(List<Transaction> all, String period) {
        List<Transaction> filtered = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        Date now = new Date();
        cal.setTime(now);

        // Define bounds
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        Date startBound;
        if ("Daily".equalsIgnoreCase(period)) {
            startBound = cal.getTime();
        } else if ("Quarterly".equalsIgnoreCase(period)) {
            cal.add(Calendar.MONTH, -3);
            startBound = cal.getTime();
        } else if ("Yearly".equalsIgnoreCase(period)) {
            cal.set(Calendar.MONTH, Calendar.JANUARY);
            cal.set(Calendar.DAY_OF_MONTH, 1);
            startBound = cal.getTime();
        } else { // "Monthly" as default
            cal.set(Calendar.DAY_OF_MONTH, 1);
            startBound = cal.getTime();
        }

        for (Transaction t : all) {
            if (t.getDate() != null && (t.getDate().after(startBound) || t.getDate().equals(startBound))) {
                filtered.add(t);
            }
        }
        return filtered;
    }

    private void calculateKPIs(List<Transaction> filtered) {
        double income = 0;
        double spent = 0;
        for (Transaction t : filtered) {
            if ("income".equalsIgnoreCase(t.getType())) {
                income += t.getAmount();
            } else if ("expense".equalsIgnoreCase(t.getType())) {
                spent += t.getAmount();
            }
        }
        totalIncome.setValue(income);
        totalSpent.setValue(spent);
        totalBalance.setValue(income - spent);
    }

    private void buildLineChartData(List<Transaction> filtered) {
        // Build cumulative expense trend sorted by date ascending
        List<Transaction> expenses = new ArrayList<>();
        for (Transaction t : filtered) {
            if ("expense".equalsIgnoreCase(t.getType())) {
                expenses.add(t);
            }
        }
        
        // Sort oldest to newest
        Collections.sort(expenses, (o1, o2) -> {
            if (o1.getDate() == null || o2.getDate() == null) return 0;
            return o1.getDate().compareTo(o2.getDate());
        });

        // Group cumulative sums by day index
        Map<Long, Double> daySumMap = new TreeMap<>();
        double cumulative = 0;
        for (Transaction t : expenses) {
            if (t.getDate() != null) {
                // Clear time to group by date
                Calendar c = Calendar.getInstance();
                c.setTime(t.getDate());
                c.set(Calendar.HOUR_OF_DAY, 0);
                c.set(Calendar.MINUTE, 0);
                c.set(Calendar.SECOND, 0);
                c.set(Calendar.MILLISECOND, 0);
                long dateMs = c.getTimeInMillis();
                
                cumulative += t.getAmount();
                daySumMap.put(dateMs, cumulative);
            }
        }

        List<Entry> entries = new ArrayList<>();
        int index = 0;
        for (Map.Entry<Long, Double> entry : daySumMap.entrySet()) {
            entries.add(new Entry(index++, entry.getValue().floatValue()));
        }
        
        // If empty, add a default point
        if (entries.isEmpty()) {
            entries.add(new Entry(0, 0f));
        }

        lineChartData.setValue(entries);
    }

    private void buildBarChartData(List<Transaction> filtered) {
        // Group by category, sum expenses
        Map<String, Double> categoryMap = new HashMap<>();
        for (Transaction t : filtered) {
            if ("expense".equalsIgnoreCase(t.getType())) {
                Double current = categoryMap.getOrDefault(t.getCategory(), 0.0);
                categoryMap.put(t.getCategory(), current + t.getAmount());
            }
        }

        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, Double> entry : categoryMap.entrySet()) {
            entries.add(new BarEntry(index++, entry.getValue().floatValue()));
            labels.add(entry.getKey());
        }

        // Default layout configuration on empty
        if (entries.isEmpty()) {
            entries.add(new BarEntry(0, 0f));
            labels.add("None");
        }

        barChartData.setValue(entries);
        barChartLabels.setValue(labels);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        TransactionRepository.getInstance().removeListener();
        if (savingsListener != null) {
            savingsListener.remove();
        }
    }
}
