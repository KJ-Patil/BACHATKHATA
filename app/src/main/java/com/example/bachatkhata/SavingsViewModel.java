package com.example.bachatkhata;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SavingsViewModel extends ViewModel {

    private final MutableLiveData<List<SavingsGoal>> savingsGoals = new MutableLiveData<>(new ArrayList<>());
    private final FirebaseFirestore mFirestore = FirebaseFirestore.getInstance();

    public LiveData<List<SavingsGoal>> getSavingsGoals() {
        return savingsGoals;
    }

    public void loadSavingsGoals(String uid) {
        mFirestore.collection("users").document(uid).collection("savings_goals")
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;

                    List<SavingsGoal> list = new ArrayList<>();
                    if (value != null) {
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            list.add(SavingsGoal.fromDocument(doc));
                        }
                    }

                    // Sort: incomplete first (by deadline ascending), completed last
                    Collections.sort(list, (g1, g2) -> {
                        boolean isG1Completed = g1.getSavedAmount() >= g1.getTargetAmount();
                        boolean isG2Completed = g2.getSavedAmount() >= g2.getTargetAmount();

                        if (isG1Completed != isG2Completed) {
                            // Incomplete first (false < true)
                            return Boolean.compare(isG1Completed, isG2Completed);
                        }

                        // Both have same completion status. Sort by deadline ascending.
                        if (g1.getDeadline() == null && g2.getDeadline() == null) return 0;
                        if (g1.getDeadline() == null) return 1; // place null deadlines at the bottom
                        if (g2.getDeadline() == null) return -1;

                        return g1.getDeadline().compareTo(g2.getDeadline());
                    });

                    savingsGoals.setValue(list);
                });
    }
}
