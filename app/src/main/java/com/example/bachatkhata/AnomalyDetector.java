package com.example.bachatkhata;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.example.bachatkhata.domain.AnomalyRadar;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
        cal.add(Calendar.DAY_OF_YEAR, -AnomalyRadar.BASELINE_WINDOW_DAYS);
        Date windowStart = cal.getTime();

        mFirestore.collection("users").document(uid).collection("transactions")
                .whereEqualTo("category", newTxn.getCategory())
                .whereEqualTo("type", "expense")
                .whereGreaterThanOrEqualTo("date", windowStart)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<AnomalyRadar.Sample> samples = new ArrayList<>();

                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        // The spend being checked must not sit in its own baseline —
                        // it would drag the median toward itself and mask the outlier.
                        if (newTxn.getId() != null && newTxn.getId().equals(doc.getId())) continue;

                        Double amt = doc.getDouble("amount");
                        Date when = doc.getDate("date");
                        if (amt != null && when != null) {
                            samples.add(new AnomalyRadar.Sample(amt, when));
                        }
                    }

                    AnomalyRadar.Result result =
                            AnomalyRadar.check(newTxn.getAmount(), samples, new Date());

                    Log.d(TAG, String.format(Locale.US,
                            "Category: %s, Median: %.2f, New Txn: %.2f, Threshold: %.2f, Samples: %d",
                            newTxn.getCategory(), result.baseline, newTxn.getAmount(),
                            result.threshold, samples.size()));

                    if (result.anomalous) {
                        triggerAnomalyAlert(context, uid, newTxn, result.baseline);
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error checking anomalies", e));
    }

    private void triggerAnomalyAlert(Context context, String uid, Transaction txn, double baseline) {
        // Respect the "Enable Notifications" master switch.
        if (!NotificationSettings.isEnabled(context)) {
            return;
        }

        String title = "Unusual Spend Alert";
        String message = String.format(Locale.US,
                "You spent %s%d on %s, more than %.1fx your usual %s%.2f for that category.",
                txn.getCurrencySymbol(), (int) txn.getAmount(), txn.getCategory(),
                AnomalyRadar.THRESHOLD_MULTIPLIER, txn.getCurrencySymbol(), baseline);

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
