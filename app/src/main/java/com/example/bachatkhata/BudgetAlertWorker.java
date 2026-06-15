package com.example.bachatkhata;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class BudgetAlertWorker extends Worker {

    private final FirebaseFirestore mFirestore;

    public BudgetAlertWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mFirestore = FirebaseFirestore.getInstance();
    }

    @NonNull
    @Override
    public Result doWork() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            return Result.success();
        }

        String uid = auth.getCurrentUser().getUid();

        try {
            Calendar cal = Calendar.getInstance();
            int month = cal.get(Calendar.MONTH);
            int year = cal.get(Calendar.YEAR);

            // Fetch current month's budgets
            QuerySnapshot budgetSnapshots = Tasks.await(
                    mFirestore.collection("users").document(uid).collection("budgets")
                            .whereEqualTo("month", month)
                            .whereEqualTo("year", year)
                            .get()
            );

            if (budgetSnapshots.isEmpty()) {
                return Result.success();
            }

            // Set start of month boundary
            Calendar boundaryCal = Calendar.getInstance();
            boundaryCal.set(year, month, 1, 0, 0, 0);
            boundaryCal.set(Calendar.MILLISECOND, 0);
            Date startBound = boundaryCal.getTime();

            // Fetch current month's expenses
            QuerySnapshot transactionSnapshots = Tasks.await(
                    mFirestore.collection("users").document(uid).collection("transactions")
                            .whereEqualTo("type", "expense")
                            .get()
            );

            // Group transaction expenses by category
            Map<String, Double> categorySpentMap = new HashMap<>();
            for (DocumentSnapshot doc : transactionSnapshots.getDocuments()) {
                Timestamp timestamp = doc.getTimestamp("date");
                if (timestamp != null && (timestamp.toDate().after(startBound) || timestamp.toDate().equals(startBound))) {
                    String categoryName = doc.getString("category");
                    Double amount = doc.getDouble("amount");
                    if (categoryName != null && amount != null) {
                        double current = categorySpentMap.getOrDefault(categoryName, 0.0);
                        categorySpentMap.put(categoryName, current + amount);
                    }
                }
            }

            // Verify budgets
            for (DocumentSnapshot budgetDoc : budgetSnapshots.getDocuments()) {
                String category = budgetDoc.getString("category");
                Double limitAmount = budgetDoc.getDouble("limitAmount");

                if (category != null && limitAmount != null && limitAmount > 0) {
                    double totalSpent = categorySpentMap.getOrDefault(category, 0.0);
                    double percentage = (totalSpent / limitAmount) * 100;

                    if (percentage >= 100) {
                        triggerLocalNotification(
                                "Budget Exceeded: " + category,
                                String.format("You spent %s of your budget limit %s for %s.",
                                        CurrencyManager.getInstance().formatAmount(totalSpent),
                                        CurrencyManager.getInstance().formatAmount(limitAmount),
                                        category),
                                uid
                        );
                    } else if (percentage >= 80) {
                        triggerLocalNotification(
                                "Budget Alert: " + category,
                                String.format("You have used %.1f%% of your budget limit %s for %s.",
                                        percentage,
                                        CurrencyManager.getInstance().formatAmount(limitAmount),
                                        category),
                                uid
                        );
                    }
                }
            }

            return Result.success();

        } catch (Exception e) {
            e.printStackTrace();
            return Result.retry();
        }
    }

    private void triggerLocalNotification(String title, String message, String uid) {
        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "budget_alerts";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Budget Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Budgets warnings and thresholds exceeded alerts.");
            notificationManager.createNotificationChannel(channel);
        }

        android.content.Intent intent = new android.content.Intent(getApplicationContext(), MainActivity.class);
        intent.putExtra("destination", "budget");
        intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                getApplicationContext(),
                (int) System.currentTimeMillis(),
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());

        // Also save this notification inside Firestore collection for user notifications inbox
        saveNotificationToFirestore(title, message, uid);
    }

    private void saveNotificationToFirestore(String title, String message, String uid) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("title", title);
        notification.put("message", message);
        notification.put("type", "alert");
        notification.put("isRead", false);
        notification.put("createdAt", Timestamp.now());

        mFirestore.collection("users").document(uid).collection("notifications").add(notification);
    }
}
