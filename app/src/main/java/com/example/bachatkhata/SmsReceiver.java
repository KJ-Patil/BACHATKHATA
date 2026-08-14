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
            if (pdus == null) return;

            // A long SMS arrives as several PDUs carrying consecutive slices of ONE
            // message. Parsing each slice on its own splits the amount from its
            // context — often mid-number — so a multipart bank alert never matched and
            // was dropped. Reassemble the body first, then parse once.
            StringBuilder fullBody = new StringBuilder();
            String sender = null;

            for (Object pdu : pdus) {
                SmsMessage smsMessage;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    smsMessage = SmsMessage.createFromPdu((byte[]) pdu, format);
                } else {
                    smsMessage = SmsMessage.createFromPdu((byte[]) pdu);
                }
                if (smsMessage == null) continue;

                String part = smsMessage.getMessageBody();
                if (part != null) fullBody.append(part);
                if (sender == null) sender = smsMessage.getOriginatingAddress();
            }

            if (sender == null || fullBody.length() == 0) return;

            if (isBankSender(sender)) {
                SmsParser.ParsedTransaction parsedTxn = SmsParser.parse(fullBody.toString());
                if (parsedTxn != null) {
                    triggerNotification(context, parsedTxn);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Whether a sender header looks like a bank/service short code rather than a
     * person.
     *
     * <p>Indian headers are assigned under TRAI's DLT scheme and normally look like
     * {@code AD-HDFCBK-S}: a two-character operator prefix, the registered sender id,
     * and a one-letter category suffix (S/T/P/G) added in 2021. The old check required
     * exactly two dash-separated parts, so every header carrying that suffix — which is
     * most of them now — failed and the message was discarded. Some circles also emit a
     * bare header with no prefix at all.
     *
     * <p>Deliberately permissive: a false positive costs nothing, because
     * {@link SmsParser#parse} still has to find a real transaction in the body before
     * anything is shown. A false negative silently loses the alert.
     */
    private boolean isBankSender(String sender) {
        String header = sender.toUpperCase(java.util.Locale.US).trim();

        // A real phone number is a person, not a service. Short codes never look
        // like this, so this is the one case worth excluding outright.
        if (header.matches("^\\+?[0-9]{7,15}$")) return false;

        // XX-SENDER, XX-SENDER-S, or a bare alphanumeric short code.
        return header.matches("^[A-Z]{2}-[A-Z0-9]{2,}(-[A-Z])?$")
                || header.matches("^[A-Z0-9]{3,11}$");
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
