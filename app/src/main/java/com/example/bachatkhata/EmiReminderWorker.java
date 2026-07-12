package com.example.bachatkhata;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class EmiReminderWorker extends Worker {

    public EmiReminderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Respect the "Enable Notifications" master switch.
        if (!NotificationSettings.isEnabled(getApplicationContext())) {
            return Result.success();
        }

        String loanName = getInputData().getString("loanName");
        double emiAmount = getInputData().getDouble("emiAmount", 0.0);

        if (loanName == null) {
            loanName = "Loan";
        }

        String title = "EMI Due Reminder";
        String message = String.format("Your monthly EMI of %s for '%s' is due in 2 days. Make sure to maintain sufficient balance.",
                CurrencyManager.getInstance().formatAmount(emiAmount),
                loanName);

        triggerLocalNotification(title, message);
        return Result.success();
    }

    private void triggerLocalNotification(String title, String message) {
        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "emi_reminders";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "EMI Reminders",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Reminders for upcoming EMI payments.");
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }
}
