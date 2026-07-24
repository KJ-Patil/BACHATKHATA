package com.example.bachatkhata;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BudgetViewModel extends ViewModel {

    private final MutableLiveData<List<Budget>> budgets = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Map<String, Double>> spentPerCategory = new MutableLiveData<>(new HashMap<>());
    /** The target month's expense transactions — what the Budgeting Rule buckets. */
    private final MutableLiveData<List<Transaction>> monthExpenses = new MutableLiveData<>(new ArrayList<>());
    /** The user's categories, needed to resolve each expense to its bucket. */
    private final MutableLiveData<List<Category>> categories = new MutableLiveData<>(new ArrayList<>());
    /** The target month's income — used for the Investment Returns rollup. */
    private final MutableLiveData<List<Transaction>> monthIncome = new MutableLiveData<>(new ArrayList<>());

    private final FirebaseFirestore mFirestore = FirebaseFirestore.getInstance();

    public LiveData<List<Budget>> getBudgets() {
        return budgets;
    }

    public LiveData<Map<String, Double>> getSpentPerCategory() {
        return spentPerCategory;
    }

    public LiveData<List<Transaction>> getMonthExpenses() {
        return monthExpenses;
    }

    public LiveData<List<Category>> getCategories() {
        return categories;
    }

    public LiveData<List<Transaction>> getMonthIncome() {
        return monthIncome;
    }

    public void loadCategories(String uid) {
        mFirestore.collection("users").document(uid).collection("categories")
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<Category> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        // Archived categories are included on purpose: old transactions
                        // still carry their names and must keep resolving to their bucket.
                        list.add(Category.fromDocument(doc));
                    }
                    categories.setValue(list);
                });
    }

    public void loadBudgetData(String uid, int month, int year) {
        // 1. Fetch budgets for targeted month/year
        mFirestore.collection("users").document(uid).collection("budgets")
                .whereEqualTo("month", month)
                .whereEqualTo("year", year)
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;

                    List<Budget> budgetList = new ArrayList<>();
                    if (value != null) {
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            budgetList.add(Budget.fromDocument(doc));
                        }
                    }
                    budgets.setValue(budgetList);

                    // Re-calculate after budgets change
                    calculateSpentPerCategory(uid, month, year);
                });
    }

    private void calculateSpentPerCategory(String uid, int month, int year) {
        // Determine start and end date boundaries for target month/year
        Calendar cal = Calendar.getInstance();
        cal.set(year, month, 1, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date startBound = cal.getTime();

        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        Date endBound = cal.getTime();

        // Fetches both directions: expenses drive the buckets, income drives the
        // Investment Returns rollup on the Spent vs Invested tab.
        mFirestore.collection("users").document(uid).collection("transactions")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Map<String, Double> map = new HashMap<>();
                    List<Transaction> expenses = new ArrayList<>();
                    List<Transaction> income = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        Timestamp dateStamp = doc.getTimestamp("date");
                        if (dateStamp == null) continue;

                        Date txnDate = dateStamp.toDate();
                        if (txnDate.before(startBound) || txnDate.after(endBound)) continue;

                        Transaction t = Transaction.fromDocument(doc);
                        if ("income".equalsIgnoreCase(t.getType())) {
                            income.add(t);
                            continue;
                        }
                        if (!"expense".equalsIgnoreCase(t.getType())) continue;

                        expenses.add(t);
                        String category = t.getCategory();
                        if (category != null) {
                            map.put(category, map.getOrDefault(category, 0.0) + t.getAmount());
                        }
                    }
                    spentPerCategory.setValue(map);
                    monthExpenses.setValue(expenses);
                    monthIncome.setValue(income);
                });
    }
}
