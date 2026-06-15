package com.example.bachatkhata;

import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GamificationManager {
    private static final String TAG = "GamificationManager";
    private static GamificationManager instance;
    private final FirebaseFirestore mFirestore;

    private GamificationManager() {
        mFirestore = FirebaseFirestore.getInstance();
    }

    public static synchronized GamificationManager getInstance() {
        if (instance == null) {
            instance = new GamificationManager();
        }
        return instance;
    }

    /**
     * Updates the user's daily streak based on the current transaction date.
     */
    public void updateDailyStreak(String uid) {
        if (uid == null || uid.isEmpty()) return;

        DocumentReference userRef = mFirestore.collection("users").document(uid);
        userRef.get().addOnSuccessListener(documentSnapshot -> {
            if (!documentSnapshot.exists()) return;

            int currentStreak = 0;
            if (documentSnapshot.contains("dailyStreak")) {
                Long streakLong = documentSnapshot.getLong("dailyStreak");
                if (streakLong != null) {
                    currentStreak = streakLong.intValue();
                }
            }

            int longestStreak = 0;
            if (documentSnapshot.contains("longestStreak")) {
                Long longestLong = documentSnapshot.getLong("longestStreak");
                if (longestLong != null) {
                    longestStreak = longestLong.intValue();
                }
            }

            String lastTxnDateStr = documentSnapshot.getString("lastTransactionDate");
            
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            String todayStr = dateFormat.format(new Date());

            int newStreak = currentStreak;
            boolean updateNeeded = false;

            if (lastTxnDateStr == null || lastTxnDateStr.isEmpty()) {
                newStreak = 1;
                updateNeeded = true;
            } else {
                try {
                    Date lastDate = dateFormat.parse(lastTxnDateStr);
                    Date todayDate = dateFormat.parse(todayStr);

                    long diffInMillis = Math.abs(todayDate.getTime() - lastDate.getTime());
                    long diffInDays = diffInMillis / (24 * 60 * 60 * 1000);

                    if (diffInDays == 1) {
                        newStreak = currentStreak + 1;
                        updateNeeded = true;
                    } else if (diffInDays > 1) {
                        newStreak = 1;
                        updateNeeded = true;
                    }
                    // If diffInDays == 0 (already logged today), do not increment but no reset either.
                } catch (Exception e) {
                    newStreak = 1;
                    updateNeeded = true;
                }
            }

            if (updateNeeded || currentStreak == 0) {
                int finalLongestStreak = Math.max(longestStreak, newStreak);
                Map<String, Object> updates = new HashMap<>();
                updates.put("dailyStreak", newStreak);
                updates.put("longestStreak", finalLongestStreak);
                updates.put("lastTransactionDate", todayStr);

                final int finalStreak = newStreak;
                userRef.update(updates).addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Streak updated to: " + finalStreak);
                    // Check streak badges
                    if (finalStreak >= 7) {
                        checkAndAwardBadge(uid, "streak_7");
                    }
                    if (finalStreak >= 30) {
                        checkAndAwardBadge(uid, "streak_30");
                    }
                }).addOnFailureListener(e -> Log.e(TAG, "Failed to update streak", e));
            }
        });
    }

    /**
     * Checks and awards a badge to the user.
     */
    public void checkAndAwardBadge(String uid, String badgeId) {
        if (uid == null || uid.isEmpty() || badgeId == null || badgeId.isEmpty()) return;

        DocumentReference userRef = mFirestore.collection("users").document(uid);
        userRef.get().addOnSuccessListener(documentSnapshot -> {
            if (!documentSnapshot.exists()) return;

            List<String> badges = (List<String>) documentSnapshot.get("awardedBadges");
            if (badges == null) {
                badges = new ArrayList<>();
            }

            if (!badges.contains(badgeId)) {
                userRef.update("awardedBadges", FieldValue.arrayUnion(badgeId))
                        .addOnSuccessListener(aVoid -> Log.d(TAG, "Badge awarded: " + badgeId))
                        .addOnFailureListener(e -> Log.e(TAG, "Failed to award badge", e));
            }
        });
    }

    /**
     * Checks monthly savings and budget compliance to award badges.
     */
    public void checkMonthlyAchievements(String uid) {
        if (uid == null || uid.isEmpty()) return;

        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH); // 0-11

        // Fetch transactions for the current month
        Calendar startCal = Calendar.getInstance();
        startCal.set(year, month, 1, 0, 0, 0);
        Date startDate = startCal.getTime();

        Calendar endCal = Calendar.getInstance();
        endCal.set(year, month, endCal.getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59);
        Date endDate = endCal.getTime();

        mFirestore.collection("users").document(uid).collection("transactions")
                .whereGreaterThanOrEqualTo("date", startDate)
                .whereLessThanOrEqualTo("date", endDate)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    double totalIncome = 0;
                    double totalExpense = 0;

                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        Double amount = doc.getDouble("amount");
                        String type = doc.getString("type");
                        if (amount != null && type != null) {
                            if ("income".equals(type)) {
                                totalIncome += amount;
                            } else if ("expense".equals(type)) {
                                totalExpense += amount;
                            }
                        }
                    }

                    double savings = totalIncome - totalExpense;
                    if (savings >= 1000) {
                        checkAndAwardBadge(uid, "saver_1000");
                    }
                    if (savings >= 10000) {
                        checkAndAwardBadge(uid, "saver_10000");
                    }

                    // Check budget limits
                    checkBudgetCompliance(uid, totalExpense);
                });
    }

    private void checkBudgetCompliance(String uid, double totalExpense) {
        // Fetch budgets to see if any exceeded
        mFirestore.collection("users").document(uid).collection("budgets")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) return;

                    boolean hasBudgets = false;
                    boolean exceeded = false;

                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        hasBudgets = true;
                        Double limit = doc.getDouble("limit");
                        Double spent = doc.getDouble("spent");
                        if (limit != null && spent != null && spent > limit) {
                            exceeded = true;
                            break;
                        }
                    }

                    if (hasBudgets && !exceeded) {
                        checkAndAwardBadge(uid, "budget_champion");
                    }
                });
    }
}
