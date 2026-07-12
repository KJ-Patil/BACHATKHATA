package com.example.bachatkhata;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Central gate for the "Enable Notifications" master switch in Profile & Settings.
 *
 * <p>The switch is persisted to Firestore ({@code users/{uid}.notificationsEnabled}) for
 * cross-device sync, and mirrored into device-local {@link SharedPreferences} so it can be read
 * synchronously from any thread — including the main thread and broadcast receivers where a
 * blocking Firestore read would risk an ANR.
 *
 * <p>When the switch is {@code false} the app suppresses every user-facing notification (budget
 * alerts, bill/EMI reminders, weekly insights, mood check-ins, health score updates, anomaly and
 * SMS-detected transaction alerts). Background data that also powers on-screen cards is still
 * computed; only the notification itself is withheld.
 */
public final class NotificationSettings {

    private static final String PREFS = "notification_prefs";
    private static final String KEY_ENABLED = "notificationsEnabled";

    private NotificationSettings() {
    }

    /**
     * @return {@code true} unless the user has explicitly turned notifications off. Defaults to
     * enabled when the preference has never been set. Safe to call from any thread.
     */
    public static boolean isEnabled(Context context) {
        if (context == null) {
            return true;
        }
        return prefs(context).getBoolean(KEY_ENABLED, true);
    }

    /**
     * Mirrors the master switch value locally. Call this whenever the switch is toggled or the
     * stored value is loaded from Firestore, so background workers and receivers see it.
     */
    public static void setEnabled(Context context, boolean enabled) {
        if (context == null) {
            return;
        }
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
