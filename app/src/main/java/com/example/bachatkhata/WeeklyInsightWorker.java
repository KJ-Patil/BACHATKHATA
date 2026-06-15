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

public class WeeklyInsightWorker extends Worker {

    private final FirebaseFirestore mFirestore;

    public WeeklyInsightWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mFirestore = FirebaseFirestore.getInstance();
    }

    @NonNull
    @Override
    public Result doWork() {
        System.out.println("WeeklyInsightWorker: Starting check");
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            return Result.success();
        }

        String uid = auth.getCurrentUser().getUid();

        try {
            // 1. Calculate time boundaries (current week = last 7 days, previous week = 8-14 days ago)
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            
            // 14 days ago start
            Calendar cal14 = (Calendar) cal.clone();
            cal14.add(Calendar.DAY_OF_YEAR, -14);
            Date startBound14 = cal14.getTime();

            // 7 days ago start
            Calendar cal7 = (Calendar) cal.clone();
            cal7.add(Calendar.DAY_OF_YEAR, -7);
            Date startBound7 = cal7.getTime();

            // Fetch expenses in the last 14 days
            QuerySnapshot transactionSnapshots = Tasks.await(
                    mFirestore.collection("users").document(uid).collection("transactions")
                            .whereEqualTo("type", "expense")
                            .whereGreaterThanOrEqualTo("date", startBound14)
                            .get()
            );

            double currentWeekSpent = 0;
            double previousWeekSpent = 0;

            Map<String, Double> currentWeekCategoryMap = new HashMap<>();
            Map<String, Double> previousWeekCategoryMap = new HashMap<>();

            int smallPurchasesCount = 0;
            double smallPurchasesSum = 0;

            for (DocumentSnapshot doc : transactionSnapshots.getDocuments()) {
                Timestamp timestamp = doc.getTimestamp("date");
                if (timestamp == null) continue;

                Date date = timestamp.toDate();
                Double amountVal = doc.getDouble("amount");
                double amount = amountVal != null ? amountVal : 0;
                String category = doc.getString("category");
                if (category == null) category = "Other";

                if (date.after(startBound7) || date.equals(startBound7)) {
                    // Current week
                    currentWeekSpent += amount;
                    currentWeekCategoryMap.put(category, currentWeekCategoryMap.getOrDefault(category, 0.0) + amount);

                    if (amount < 100) {
                        smallPurchasesCount++;
                        smallPurchasesSum += amount;
                    }
                } else if (date.after(startBound14) || date.equals(startBound14)) {
                    // Previous week
                    previousWeekSpent += amount;
                    previousWeekCategoryMap.put(category, previousWeekCategoryMap.getOrDefault(category, 0.0) + amount);
                }
            }

            // 2. Compute Rule-Based Insights
            String title = "Weekly Insight";
            String message = "";

            // Check Rule 1: Week-over-Week decrease
            if (currentWeekSpent < previousWeekSpent && previousWeekSpent > 0) {
                double diff = previousWeekSpent - currentWeekSpent;
                double percentDecrease = (diff / previousWeekSpent) * 100;
                message = String.format("Awesome! Your weekly spending decreased by %.1f%% compared to last week (₹%d vs ₹%d). Keep up the good work!",
                        percentDecrease, (int)currentWeekSpent, (int)previousWeekSpent);
            } 
            
            // Check Rule 2: Category spike (>50% increase and spend > ₹200)
            if (message.isEmpty()) {
                String spikeCategory = null;
                double maxSpikePercent = 0;
                double currentSpikeAmount = 0;
                double previousSpikeAmount = 0;

                for (Map.Entry<String, Double> entry : currentWeekCategoryMap.entrySet()) {
                    String cat = entry.getKey();
                    double currAmt = entry.getValue();
                    double prevAmt = previousWeekCategoryMap.getOrDefault(cat, 0.0);

                    if (currAmt > 200 && currAmt > prevAmt) {
                        double increase = currAmt - prevAmt;
                        double percentIncrease = prevAmt > 0 ? (increase / prevAmt) * 100 : 100;

                        if (percentIncrease >= 50 && percentIncrease > maxSpikePercent) {
                            maxSpikePercent = percentIncrease;
                            spikeCategory = cat;
                            currentSpikeAmount = currAmt;
                            previousSpikeAmount = prevAmt;
                        }
                    }
                }

                if (spikeCategory != null) {
                    message = String.format("Careful! Your spending on %s shot up by %.1f%% this week (spent ₹%d vs ₹%d last week). Try to set a budget limit.",
                            spikeCategory, maxSpikePercent, (int)currentSpikeAmount, (int)previousSpikeAmount);
                }
            }

            // Check Rule 3: Small purchases aggregate (>4 micro-spends)
            if (message.isEmpty() && smallPurchasesCount >= 4) {
                message = String.format("Budget Tip: You made %d small purchases under ₹100 this week, adding up to ₹%d. Watch out for these micro-spends!",
                        smallPurchasesCount, (int)smallPurchasesSum);
            }

            // Fallback Rule: Basic summary
            if (message.isEmpty()) {
                message = String.format("You spent a total of ₹%d this past week. Keep tracking your expenses regularly to stay financially disciplined.",
                        (int)currentWeekSpent);
            }

            // Save insight message to the user document so it can be loaded on the Home dashboard
            Map<String, Object> userUpdates = new HashMap<>();
            userUpdates.put("weeklyInsight", message);
            userUpdates.put("weeklyInsightTime", Timestamp.now());
            Tasks.await(mFirestore.collection("users").document(uid).update(userUpdates));

            // Trigger notification
            triggerLocalNotification(title, message, uid);
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
            notificationManager.createNotificationChannel(channel);
        }

        android.content.Intent intent = new android.content.Intent(getApplicationContext(), MainActivity.class);
        intent.putExtra("destination", "transactions");
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
        saveNotificationToFirestore(title, message, uid);
    }

    private void saveNotificationToFirestore(String title, String message, String uid) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("title", title);
        notification.put("message", message);
        notification.put("type", "insight");
        notification.put("isRead", false);
        notification.put("createdAt", Timestamp.now());

        mFirestore.collection("users").document(uid).collection("notifications").add(notification);
    }
}
