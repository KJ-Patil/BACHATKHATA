package com.example.bachatkhata;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class AnomalyDetector {
    private static final String TAG = "AnomalyDetector";
    private static AnomalyDetector instance;
    private final FirebaseFirestore mFirestore;

    private AnomalyDetector() {
        mFirestore = FirebaseFirestore.getInstance();
    }

    public static synchronized AnomalyDetector getInstance() {
        if (instance == null) {
            instance = new AnomalyDetector();
        }
        return instance;
    }

    /**
     * Checks if the newly logged transaction is an anomaly (> 2.5x the rolling 30-day category average).
     */
    public void checkForAnomaly(Context context, String uid, Transaction newTxn) {
        if (uid == null || newTxn == null || !"expense".equals(newTxn.getType())) return;

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -30);
        Date thirtyDaysAgo = cal.getTime();

        mFirestore.collection("users").document(uid).collection("transactions")
                .whereEqualTo("category", newTxn.getCategory())
                .whereEqualTo("type", "expense")
                .whereGreaterThanOrEqualTo("date", thirtyDaysAgo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    double totalAmount = 0;
                    int count = 0;

                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        // Skip the current new transaction if it has been written to DB
                        if (newTxn.getId().equals(doc.getId())) continue;

                        Double amt = doc.getDouble("amount");
                        if (amt != null) {
                            totalAmount += amt;
                            count++;
                        }
                    }

                    if (count < 3) {
                        // Not enough data to determine a trend
                        Log.d(TAG, "Not enough data for anomaly detection. Count: " + count);
                        return;
                    }

                    double average = totalAmount / count;
                    double threshold = average * 2.5;

                    Log.d(TAG, String.format("Category: %s, Average: %.2f, New Txn: %.2f, Threshold: %.2f", 
                            newTxn.getCategory(), average, newTxn.getAmount(), threshold));

                    if (newTxn.getAmount() > threshold) {
                        triggerAnomalyAlert(context, uid, newTxn, average);
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error checking anomalies", e));
    }

    private void triggerAnomalyAlert(Context context, String uid, Transaction txn, double average) {
        // Respect the "Enable Notifications" master switch.
        if (!NotificationSettings.isEnabled(context)) {
            return;
        }

        String title = "Unusual Spend Alert";
        String message = String.format("You spent %s%s on %s, which is 2.5x higher than your usual 30-day average of %s%.2f.",
                txn.getCurrencySymbol(), (int)txn.getAmount(), txn.getCategory(), txn.getCurrencySymbol(), average);

        Log.d(TAG, "Anomaly detected! Posting notification: " + message);

        // 1. Post local notification
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "AnomalyAlerts";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Unusual Spending Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Alerts you when a transaction is significantly higher than usual.");
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true);

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());

        // 2. Save alert inside Firestore notifications collection
        Map<String, Object> notification = new HashMap<>();
        notification.put("title", title);
        notification.put("message", message);
        notification.put("type", "alert");
        notification.put("isRead", false);
        notification.put("createdAt", Timestamp.now());

        mFirestore.collection("users").document(uid).collection("notifications")
                .add(notification)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to write anomaly notification to database", e));
    }
}
