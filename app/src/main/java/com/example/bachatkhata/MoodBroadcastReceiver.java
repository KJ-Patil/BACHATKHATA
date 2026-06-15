package com.example.bachatkhata;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MoodBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "MoodBroadcastReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pendingResult = goAsync();

        // Dismiss notification
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(777);
        }

        String action = intent.getAction();
        String uid = intent.getStringExtra("uid");
        if (uid == null || uid.isEmpty()) {
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            }
        }

        if (uid == null || uid.isEmpty() || action == null) {
            pendingResult.finish();
            return;
        }

        String mood;
        if ("MOOD_GOOD".equals(action)) {
            mood = "Good";
        } else if ("MOOD_OKAY".equals(action)) {
            mood = "Okay";
        } else if ("MOOD_STRESSED".equals(action)) {
            mood = "Stressed";
        } else {
            pendingResult.finish();
            return;
        }

        FirebaseFirestore mFirestore = FirebaseFirestore.getInstance();

        // Calculate week start date (last Monday)
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int daysToSubtract = (dayOfWeek - Calendar.MONDAY + 7) % 7;
        cal.add(Calendar.DAY_OF_YEAR, -daysToSubtract);
        Date weekStartDate = cal.getTime();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String weekKey = sdf.format(weekStartDate);

        // Fetch transactions to calculate weekly spend
        final String finalUid = uid;
        final String finalMood = mood;

        mFirestore.collection("users").document(finalUid).collection("transactions")
                .whereGreaterThanOrEqualTo("date", weekStartDate)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    double totalSpend = 0.0;
                    if (querySnapshot != null) {
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            String type = doc.getString("type");
                            Double amount = doc.getDouble("amount");
                            if ("expense".equalsIgnoreCase(type) && amount != null) {
                                totalSpend += amount;
                            }
                        }
                    }

                    double avgSpend = totalSpend / 7.0;

                    // Save mood log
                    Map<String, Object> log = new HashMap<>();
                    log.put("mood", finalMood);
                    log.put("weekStartDate", weekStartDate);
                    log.put("totalSpendThatWeek", totalSpend);
                    log.put("avgSpendThatWeek", avgSpend);
                    log.put("timestamp", Timestamp.now());

                    mFirestore.collection("users").document(finalUid)
                            .collection("mood_logs").document(weekKey)
                            .set(log)
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "Mood log saved successfully for week: " + weekKey);
                                pendingResult.finish();
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to save mood log", e);
                                pendingResult.finish();
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to query transactions for mood log", e);
                    pendingResult.finish();
                });
    }
}
