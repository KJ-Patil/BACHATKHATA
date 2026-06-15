package com.example.bachatkhata;

import android.content.Context;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RoundUpSavingsManager {

    private static RoundUpSavingsManager instance;
    private final FirebaseFirestore mFirestore;
    private final FirebaseAuth mAuth;

    private RoundUpSavingsManager() {
        mFirestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
    }

    public static synchronized RoundUpSavingsManager getInstance() {
        if (instance == null) {
            instance = new RoundUpSavingsManager();
        }
        return instance;
    }

    public double calculateRoundUpAmount(double transactionAmount, int roundUpLimit) {
        if (transactionAmount <= 0) return 0.0;
        double nextMultiple = Math.ceil(transactionAmount / roundUpLimit) * roundUpLimit;
        double roundUp = nextMultiple - transactionAmount;
        // If it is already a multiple, roundUp is 0.0
        return Math.max(0.0, roundUp);
    }

    public void processRoundUp(Context context, double amount) {
        SharedPreferencesManager prefs = SharedPreferencesManager.getInstance(context);
        if (!prefs.isRoundUpEnabled() || mAuth.getCurrentUser() == null) {
            return;
        }

        int limit = prefs.getRoundUpLimit();
        double roundUpAmount = calculateRoundUpAmount(amount, limit);

        if (roundUpAmount <= 0.01) {
            return; // Ignore extremely tiny or zero round-ups
        }

        String uid = mAuth.getCurrentUser().getUid();

        // Search for existing "Round-up jar" goal
        mFirestore.collection("users").document(uid).collection("savings_goals")
                .whereEqualTo("name", "Round-up jar")
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) return;

                    QuerySnapshot snap = task.getResult();
                    if (!snap.isEmpty()) {
                        // Goal exists
                        DocumentSnapshot doc = snap.getDocuments().get(0);
                        SavingsGoal goal = SavingsGoal.fromDocument(doc);
                        updateGoalWithRoundUp(uid, goal, roundUpAmount);
                    } else {
                        // Goal does not exist, create it
                        createRoundUpJarGoal(uid, prefs.getUserCurrency(), roundUpAmount);
                    }
                });
    }

    private void updateGoalWithRoundUp(String uid, SavingsGoal goal, double roundUpAmount) {
        double newSaved = goal.getSavedAmount() + roundUpAmount;

        Map<String, Object> transaction = new HashMap<>();
        transaction.put("amount", roundUpAmount);
        transaction.put("date", Timestamp.now());
        transaction.put("type", "deposit");

        mFirestore.collection("users").document(uid)
                .collection("savings_goals").document(goal.getId())
                .update("savedAmount", newSaved)
                .addOnSuccessListener(aVoid -> {
                    // Record sub-collection transaction
                    mFirestore.collection("users").document(uid)
                            .collection("savings_goals").document(goal.getId())
                            .collection("transactions").add(transaction);
                });
    }

    private void createRoundUpJarGoal(String uid, String currencyCode, double initialRoundUp) {
        String goalId = UUID.randomUUID().toString();
        Timestamp now = Timestamp.now();

        SavingsGoal newGoal = new SavingsGoal(
                goalId,
                "Round-up jar",
                "🫙",
                5000.0, // Default target
                initialRoundUp,
                currencyCode,
                now, // Default deadline
                now
        );

        Map<String, Object> transaction = new HashMap<>();
        transaction.put("amount", initialRoundUp);
        transaction.put("date", now);
        transaction.put("type", "deposit");

        mFirestore.collection("users").document(uid)
                .collection("savings_goals").document(goalId)
                .set(newGoal.toMap())
                .addOnSuccessListener(aVoid -> {
                    mFirestore.collection("users").document(uid)
                            .collection("savings_goals").document(goalId)
                            .collection("transactions").add(transaction);
                });
    }
}
