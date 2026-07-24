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
    
    private final MutableLiveData<Double> todaySpent = new MutableLiveData<>(0.0);

    // Safe-to-Spend: consumes transactions + budgets + goals at once.
    private final MutableLiveData<SafeToSpendCalculator.Result> safeToSpend = new MutableLiveData<>();
    private final List<Budget> budgetsList = new ArrayList<>();
    private final List<SavingsGoal> goalsList = new ArrayList<>();
    private ListenerRegistration budgetsListener;

    // Line chart (Spending Trend) — cumulative series, aligned on a shared day axis
    private final MutableLiveData<List<Entry>> lineIncomeData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Entry>> lineSpentData = new MutableLiveData<>(new ArrayList<>());
    // Bar chart (By Category) — separate income/expense category breakdowns
    private final MutableLiveData<List<BarEntry>> barIncomeData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<String>> barIncomeLabels = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<BarEntry>> barSpentData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<String>> barSpentLabels = new MutableLiveData<>(new ArrayList<>());
    // Chart filter toggle: "Both" | "Income" | "Spent"
    private final MutableLiveData<String> chartMode = new MutableLiveData<>("Both");
    private final MutableLiveData<String> selectedPeriod = new MutableLiveData<>("Monthly");
    private String currentSearchQuery = "";

    private final FirebaseFirestore mFirestore = FirebaseFirestore.getInstance();
    private ListenerRegistration savingsListener;
    private final List<Transaction> allTransactionsList = new ArrayList<>();
    private final MutableLiveData<List<Transaction>> allTransactions = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Category>> categories = new MutableLiveData<>(new ArrayList<>());

    public LiveData<Double> getTotalIncome() { return totalIncome; }
    public LiveData<Double> getTotalSpent() { return totalSpent; }
    public LiveData<Double> getTotalBalance() { return totalBalance; }
    public LiveData<Double> getTotalSaved() { return totalSaved; }
    public LiveData<List<Transaction>> getRecentTransactions() { return recentTransactions; }
    public LiveData<Double> getTodaySpent() { return todaySpent; }
    public LiveData<SafeToSpendCalculator.Result> getSafeToSpend() { return safeToSpend; }
    public LiveData<List<Entry>> getLineIncomeData() { return lineIncomeData; }
    public LiveData<List<Entry>> getLineSpentData() { return lineSpentData; }
    public LiveData<List<BarEntry>> getBarIncomeData() { return barIncomeData; }
    public LiveData<List<String>> getBarIncomeLabels() { return barIncomeLabels; }
    public LiveData<List<BarEntry>> getBarSpentData() { return barSpentData; }
    public LiveData<List<String>> getBarSpentLabels() { return barSpentLabels; }
    public LiveData<String> getChartMode() { return chartMode; }
    public LiveData<String> getSelectedPeriod() { return selectedPeriod; }
    public LiveData<List<Transaction>> getAllTransactions() { return allTransactions; }
    public LiveData<List<Category>> getCategories() { return categories; }

    /** Categories are needed to resolve each expense to its 50/30/20 bucket. */
    public void loadCategories(String uid) {
        mFirestore.collection("users").document(uid).collection("categories")
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<Category> list = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snapshots.getDocuments()) {
                        list.add(Category.fromDocument(doc));
                    }
                    categories.setValue(list);
                });
    }

    public void changeChartMode(String mode) {
        chartMode.setValue(mode);
    }

    public void loadDashboardData(String uid, String period) {
        selectedPeriod.setValue(period);
        
        // 1. Observe Transactions in real time
        TransactionRepository.getInstance().observeTransactions(uid, list -> {
            allTransactionsList.clear();
            allTransactionsList.addAll(list);
            allTransactions.setValue(new ArrayList<>(allTransactionsList));
            processAndEmitData(period);
            recomputeSafeToSpend();
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
                    goalsList.clear();
                    if (value != null) {
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            SavingsGoal g = SavingsGoal.fromDocument(doc);
                            goalsList.add(g);
                            savedSum += g.getSavedAmount();
                        }
                    }
                    totalSaved.setValue(savedSum);
                    recomputeSafeToSpend();
                });

        // 3. Observe this month's Budgets (for the Safe-to-Spend fallback ceiling)
        if (budgetsListener != null) {
            budgetsListener.remove();
        }
        Calendar bCal = Calendar.getInstance();
        budgetsListener = mFirestore.collection("users")
                .document(uid)
                .collection("budgets")
                .whereEqualTo("month", bCal.get(Calendar.MONTH))
                .whereEqualTo("year", bCal.get(Calendar.YEAR))
                .addSnapshotListener((value, error) -> {
                    budgetsList.clear();
                    if (value != null) {
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            budgetsList.add(Budget.fromDocument(doc));
                        }
                    }
                    recomputeSafeToSpend();
                });
    }

    private void recomputeSafeToSpend() {
        safeToSpend.setValue(
                SafeToSpendCalculator.compute(allTransactionsList, budgetsList, goalsList));
    }

    public void changePeriod(String period) {
        selectedPeriod.setValue(period);
        processAndEmitData(period);
    }

    public void setSearchQuery(String query) {
        android.util.Log.d("HomeSearch", "HomeViewModel: setSearchQuery: query = '" + query + "'");
        this.currentSearchQuery = query;
        processAndEmitData(selectedPeriod.getValue());
    }

    private void processAndEmitData(String period) {
        List<Transaction> filtered = filterByPeriod(allTransactionsList, period);
        calculateKPIs(filtered);
        
        android.util.Log.d("HomeSearch", "HomeViewModel: processAndEmitData: allTransactions size = " + allTransactionsList.size() + ", filtered size = " + filtered.size());

        // Emit top 3 transactions for recent list or matching search results
        List<Transaction> recent = new ArrayList<>();
        if (currentSearchQuery == null || currentSearchQuery.trim().isEmpty()) {
            for (int i = 0; i < Math.min(3, filtered.size()); i++) {
                recent.add(filtered.get(i));
            }
        } else {
            String lowerQuery = currentSearchQuery.toLowerCase().trim();
            for (Transaction t : allTransactionsList) {
                boolean noteMatch = t.getNote() != null && t.getNote().toLowerCase().contains(lowerQuery);
                boolean categoryMatch = t.getCategory() != null && t.getCategory().toLowerCase().contains(lowerQuery);
                android.util.Log.d("HomeSearch", "HomeViewModel: comparing txn: note = '" + t.getNote() + "', cat = '" + t.getCategory() + "', noteMatch = " + noteMatch + ", categoryMatch = " + categoryMatch);
                if (noteMatch || categoryMatch) {
                    recent.add(t);
                }
            }
        }
        android.util.Log.d("HomeSearch", "HomeViewModel: Emitting recentTransactions size = " + recent.size());
        recentTransactions.setValue(recent);

        // Calculate today's spent amount from all transactions
        double spentToday = 0;
        Calendar todayCal = Calendar.getInstance();
        int todayYear = todayCal.get(Calendar.YEAR);
        int todayMonth = todayCal.get(Calendar.MONTH);
        int todayDay = todayCal.get(Calendar.DAY_OF_MONTH);
        
        for (Transaction t : allTransactionsList) {
            if ("expense".equalsIgnoreCase(t.getType()) && t.getDate() != null) {
                Calendar tCal = Calendar.getInstance();
                tCal.setTime(t.getDate());
                if (tCal.get(Calendar.YEAR) == todayYear &&
                    tCal.get(Calendar.MONTH) == todayMonth &&
                    tCal.get(Calendar.DAY_OF_MONTH) == todayDay) {
                    spentToday += t.getAmount();
                }
            }
        }
        todaySpent.setValue(spentToday);

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
        // Build cumulative income and expense trends over a shared, sorted day axis
        // so both series line up on the same x indices.
        TreeMap<Long, Double> incomeByDay = new TreeMap<>();
        TreeMap<Long, Double> spentByDay = new TreeMap<>();
        TreeMap<Long, Boolean> allDays = new TreeMap<>();
        for (Transaction t : filtered) {
            if (t.getDate() == null) continue;
            long dateMs = startOfDay(t.getDate());
            allDays.put(dateMs, true);
            if ("income".equalsIgnoreCase(t.getType())) {
                incomeByDay.put(dateMs, incomeByDay.getOrDefault(dateMs, 0.0) + t.getAmount());
            } else if ("expense".equalsIgnoreCase(t.getType())) {
                spentByDay.put(dateMs, spentByDay.getOrDefault(dateMs, 0.0) + t.getAmount());
            }
        }

        List<Entry> incomeEntries = new ArrayList<>();
        List<Entry> spentEntries = new ArrayList<>();
        // Seed a zero origin so a single day of activity still renders as a rising
        // line (0 -> value) instead of a lone, unconnected dot. A line needs >= 2
        // points, and a cumulative trend genuinely starts at zero.
        incomeEntries.add(new Entry(0, 0f));
        spentEntries.add(new Entry(0, 0f));
        int index = 1;
        double incomeCumulative = 0;
        double spentCumulative = 0;
        for (Long day : allDays.keySet()) {
            incomeCumulative += incomeByDay.getOrDefault(day, 0.0);
            spentCumulative += spentByDay.getOrDefault(day, 0.0);
            incomeEntries.add(new Entry(index, (float) incomeCumulative));
            spentEntries.add(new Entry(index, (float) spentCumulative));
            index++;
        }

        lineIncomeData.setValue(incomeEntries);
        lineSpentData.setValue(spentEntries);
    }

    private long startOfDay(Date date) {
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private void buildBarChartData(List<Transaction> filtered) {
        // Group by category, summing income and expense separately
        Map<String, Double> incomeCat = new HashMap<>();
        Map<String, Double> spentCat = new HashMap<>();
        for (Transaction t : filtered) {
            String category = t.getCategory() != null ? t.getCategory() : "Other";
            if ("income".equalsIgnoreCase(t.getType())) {
                incomeCat.put(category, incomeCat.getOrDefault(category, 0.0) + t.getAmount());
            } else if ("expense".equalsIgnoreCase(t.getType())) {
                spentCat.put(category, spentCat.getOrDefault(category, 0.0) + t.getAmount());
            }
        }

        List<BarEntry> incomeEntries = new ArrayList<>();
        List<String> incomeLabels = new ArrayList<>();
        int incomeIndex = 0;
        for (Map.Entry<String, Double> entry : incomeCat.entrySet()) {
            incomeEntries.add(new BarEntry(incomeIndex++, entry.getValue().floatValue()));
            incomeLabels.add(entry.getKey());
        }
        if (incomeEntries.isEmpty()) {
            incomeEntries.add(new BarEntry(0, 0f));
            incomeLabels.add("None");
        }

        List<BarEntry> spentEntries = new ArrayList<>();
        List<String> spentLabels = new ArrayList<>();
        int spentIndex = 0;
        for (Map.Entry<String, Double> entry : spentCat.entrySet()) {
            spentEntries.add(new BarEntry(spentIndex++, entry.getValue().floatValue()));
            spentLabels.add(entry.getKey());
        }
        if (spentEntries.isEmpty()) {
            spentEntries.add(new BarEntry(0, 0f));
            spentLabels.add("None");
        }

        barIncomeData.setValue(incomeEntries);
        barIncomeLabels.setValue(incomeLabels);
        barSpentData.setValue(spentEntries);
        barSpentLabels.setValue(spentLabels);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        TransactionRepository.getInstance().removeListener();
        if (savingsListener != null) {
            savingsListener.remove();
        }
        if (budgetsListener != null) {
            budgetsListener.remove();
        }
    }
}
