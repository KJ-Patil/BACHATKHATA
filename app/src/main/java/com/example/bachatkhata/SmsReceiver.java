package com.example.bachatkhata;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;

import androidx.core.app.NotificationCompat;

public class SmsReceiver extends BroadcastReceiver {

    private static final String SMS_RECEIVED_ACTION = "android.provider.Telephony.SMS_RECEIVED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            return;
        }

        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        try {
            Object[] pdus = (Object[]) bundle.get("pdus");
            String format = bundle.getString("format");
            if (pdus != null) {
                for (Object pdu : pdus) {
                    SmsMessage smsMessage;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        smsMessage = SmsMessage.createFromPdu((byte[]) pdu, format);
                    } else {
                        smsMessage = SmsMessage.createFromPdu((byte[]) pdu);
                    }

                    if (smsMessage == null) continue;

                    String body = smsMessage.getMessageBody();
                    String sender = smsMessage.getOriginatingAddress();

                    if (sender == null || body == null) continue;

                    // Verify sender header starts with bank prefixes (e.g. VK-, VM-, JD-)
                    String upperSender = sender.toUpperCase().trim();
                    boolean isBankSender = upperSender.startsWith("VM-") || 
                                           upperSender.startsWith("VK-") || 
                                           upperSender.startsWith("JD-") || 
                                           upperSender.matches("^[A-Z]{2}-[A-Z0-9]+$");

                    if (isBankSender) {
                        SmsParser.ParsedTransaction parsedTxn = SmsParser.parse(body);
                        if (parsedTxn != null) {
                            // Valid transaction SMS found! Post notification.
                            triggerNotification(context, parsedTxn);
                            break; // Avoid spamming multiple notifications if concatenated
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void triggerNotification(Context context, SmsParser.ParsedTransaction txn) {
        // Respect the "Enable Notifications" master switch.
        if (!NotificationSettings.isEnabled(context)) {
            return;
        }

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "sms_import_alerts";
        String channelName = "SMS Auto-Import Alerts";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Alerts for automatically detected bank SMS transactions.");
            notificationManager.createNotificationChannel(channel);
        }

        // Create Intent to open MainActivity and show bottom sheet
        Intent launchIntent = new Intent(context, MainActivity.class);
        launchIntent.putExtra("parsed_transaction", txn);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                (int) System.currentTimeMillis(),
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String merchantText = txn.merchant != null ? txn.merchant : "Unknown Merchant";
        String alertText = String.format("New transaction detected: %s — %s. Tap to save.", 
                CurrencyManager.getInstance().formatAmount(txn.amount), 
                merchantText);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("BachatKhata Auto-Import")
                .setContentText(alertText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(alertText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOngoing(false); // Make it clearable but notable

        notificationManager.notify(2002, builder.build());
    }
}
