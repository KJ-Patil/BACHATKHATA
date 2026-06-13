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

    private final FirebaseFirestore mFirestore = FirebaseFirestore.getInstance();

    public LiveData<List<Budget>> getBudgets() {
        return budgets;
    }

    public LiveData<Map<String, Double>> getSpentPerCategory() {
        return spentPerCategory;
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

        mFirestore.collection("users").document(uid).collection("transactions")
                .whereEqualTo("type", "expense")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Map<String, Double> map = new HashMap<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        Timestamp dateStamp = doc.getTimestamp("date");
                        if (dateStamp != null) {
                            Date txnDate = dateStamp.toDate();
                            if ((txnDate.after(startBound) || txnDate.equals(startBound)) && 
                                (txnDate.before(endBound) || txnDate.equals(endBound))) {
                                
                                String category = doc.getString("category");
                                Double amount = doc.getDouble("amount");
                                if (category != null && amount != null) {
                                    double current = map.getOrDefault(category, 0.0);
                                    map.put(category, current + amount);
                                }
                            }
                        }
                    }
                    spentPerCategory.setValue(map);
                });
    }
}
