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
import java.util.HashMap;
import java.util.Map;

public class BillReminderWorker extends Worker {

    private final FirebaseFirestore mFirestore;

    public BillReminderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
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

        // Respect the "Enable Notifications" master switch.
        if (!NotificationSettings.isEnabled(getApplicationContext())) {
            return Result.success();
        }

        try {
            Calendar today = Calendar.getInstance();
            int currentDay = today.get(Calendar.DAY_OF_MONTH);
            int currentMonth = today.get(Calendar.MONTH);
            int currentYear = today.get(Calendar.YEAR);

            QuerySnapshot billSnapshots = Tasks.await(
                    mFirestore.collection("users").document(uid).collection("bills")
                            .whereEqualTo("isActive", true)
                            .get()
            );

            for (DocumentSnapshot doc : billSnapshots.getDocuments()) {
                String name = doc.getString("name");
                Double amount = doc.getDouble("amount");
                Long dueDayLong = doc.getLong("dueDay");
                Timestamp lastPaid = doc.getTimestamp("lastPaidDate");

                if (name != null && dueDayLong != null) {
                    int dueDay = dueDayLong.intValue();
                    
                    // Check if already paid this month
                    boolean alreadyPaidThisMonth = false;
                    if (lastPaid != null) {
                        Calendar paidCal = Calendar.getInstance();
                        paidCal.setTime(lastPaid.toDate());
                        if (paidCal.get(Calendar.MONTH) == currentMonth && paidCal.get(Calendar.YEAR) == currentYear) {
                            alreadyPaidThisMonth = true;
                        }
                    }

                    if (!alreadyPaidThisMonth) {
                        // Alert if today is within 3 days before dueDay, or on/after dueDay
                        int daysDiff = dueDay - currentDay;
                        if (daysDiff >= 0 && daysDiff <= 3) {
                            String amountStr = amount != null && amount > 0 
                                    ? CurrencyManager.getInstance().formatAmount(amount) 
                                    : "";
                            String title = "Bill Due Soon: " + name;
                            String message = String.format("Your %s bill%s is due on the %d of this month.",
                                    name,
                                    amountStr.isEmpty() ? "" : " of " + amountStr,
                                    dueDay);

                            triggerLocalNotification(title, message, uid);
                        }
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
            notificationManager.createNotificationChannel(channel);
        }

        android.content.Intent intent = new android.content.Intent(getApplicationContext(), MainActivity.class);
        intent.putExtra("destination", "notifications");
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
        notification.put("type", "bill");
        notification.put("isRead", false);
        notification.put("createdAt", Timestamp.now());

        mFirestore.collection("users").document(uid).collection("notifications").add(notification);
    }
}
