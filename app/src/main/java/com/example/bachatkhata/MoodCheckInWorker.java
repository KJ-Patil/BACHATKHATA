package com.example.bachatkhata;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class MoodCheckInWorker extends Worker {

    public MoodCheckInWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            return Result.success();
        }

        String uid = auth.getCurrentUser().getUid();
        FirebaseFirestore mFirestore = FirebaseFirestore.getInstance();

        try {
            // Check if mood check-in is enabled
            DocumentSnapshot userDoc = Tasks.await(
                    mFirestore.collection("users").document(uid).get()
            );

            if (userDoc.exists()) {
                Boolean moodEnabled = userDoc.getBoolean("moodEnabled");
                if (moodEnabled != null && !moodEnabled) {
                    return Result.success(); // Disabled by user
                }
            }

            triggerMoodCheckInNotification(uid);
            return Result.success();

        } catch (Exception e) {
            e.printStackTrace();
            return Result.retry();
        }
    }

    private void triggerMoodCheckInNotification(String uid) {
        Context context = getApplicationContext();
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "mood_check_in";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Money & Mood Check-In",
                    NotificationManager.IMPORTANCE_HIGH
            );
            notificationManager.createNotificationChannel(channel);
        }

        // Good action intent
        Intent goodIntent = new Intent(context, MoodBroadcastReceiver.class);
        goodIntent.setAction("MOOD_GOOD");
        goodIntent.putExtra("uid", uid);
        PendingIntent goodPending = PendingIntent.getBroadcast(
                context,
                1001,
                goodIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Okay action intent
        Intent okayIntent = new Intent(context, MoodBroadcastReceiver.class);
        okayIntent.setAction("MOOD_OKAY");
        okayIntent.putExtra("uid", uid);
        PendingIntent okayPending = PendingIntent.getBroadcast(
                context,
                1002,
                okayIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Stressed action intent
        Intent stressedIntent = new Intent(context, MoodBroadcastReceiver.class);
        stressedIntent.setAction("MOOD_STRESSED");
        stressedIntent.putExtra("uid", uid);
        PendingIntent stressedPending = PendingIntent.getBroadcast(
                context,
                1003,
                stressedIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Content intent (launches MoodInsightActivity)
        Intent contentIntent = new Intent(context, MainActivity.class);
        contentIntent.putExtra("destination", "mood");
        contentIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent contentPending = PendingIntent.getActivity(
                context,
                1004,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Quick check-in")
                .setContentText("How do you feel about money this week?")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(contentPending)
                .setAutoCancel(true)
                .addAction(0, "Good", goodPending)
                .addAction(0, "Okay", okayPending)
                .addAction(0, "Stressed", stressedPending);

        notificationManager.notify(777, builder.build());
    }
}
