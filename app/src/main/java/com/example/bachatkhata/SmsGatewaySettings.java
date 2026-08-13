package com.example.bachatkhata;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-user SMS gateway credentials (Fast2SMS Quick route).
 *
 * <p>Scoped to the Quick route deliberately: no DLT registration, sender ID or
 * pre-approved templates, hence the single API-key field. The trade-off is that
 * messages come from a shared number, are reviewed before dispatch, and never
 * reach DND-registered numbers.
 *
 * <p><b>No send path exists.</b> Storing a key does not make the app send SMS.
 * Real sending needs a server route holding the key — a key shipped inside the
 * APK is extractable, so the same rule applies here as on the web. The settings
 * screen says so in a banner rather than implying otherwise.
 *
 * <p><b>The key never leaves this device.</b> It is held only in prefs, which are
 * AES-GCM encrypted at rest via {@link SharedPreferencesManager}. Firestore keeps
 * the on/off flag, which is a preference rather than a credential.
 *
 * <p>It used to be mirrored to {@code users/{uid}.smsGatewayApiKey} so it would
 * follow the user to a new device. That wrote a live third-party credential to the
 * cloud in plain text — encrypted carefully on the phone and then sent up readable,
 * which makes the on-device encryption largely beside the point. Owner-only rules
 * limit who can read it, but they do not make it not-plaintext, and the key is
 * exposed to anything with access to the project's data: an admin SDK script, a
 * console session, an export, a future rules mistake.
 *
 * <p>Convenience lost: the key must be re-entered on a new device. That is the
 * right trade for a credential the user can revoke and re-issue at will.
 */
public final class SmsGatewaySettings {

    private static final String FIELD_ENABLED = "smsGatewayEnabled";
    /**
     * Retained only so {@link #save} can erase copies written by older builds. Never
     * written with a value, never read back.
     */
    private static final String FIELD_API_KEY = "smsGatewayApiKey";

    private SmsGatewaySettings() {
    }

    /** Immutable snapshot of the stored config. */
    public static final class Config {
        public final boolean enabled;
        public final String apiKey;

        public Config(boolean enabled, String apiKey) {
            this.enabled = enabled;
            this.apiKey = apiKey == null ? "" : apiKey;
        }

        public boolean hasKey() {
            return !apiKey.trim().isEmpty();
        }
    }

    public static Config get(@NonNull Context context) {
        SharedPreferencesManager prefs = SharedPreferencesManager.getInstance(context);
        return new Config(prefs.isSmsGatewayEnabled(), prefs.getSmsGatewayApiKey());
    }

    /**
     * Saves the config. The key goes to encrypted prefs only; the cloud gets the
     * on/off flag and an explicit delete of any key an older build left behind.
     */
    public static void save(@NonNull Context context, String uid, Config config) {
        SharedPreferencesManager prefs = SharedPreferencesManager.getInstance(context);
        prefs.setSmsGatewayEnabled(config.enabled);
        prefs.setSmsGatewayApiKey(config.apiKey.trim());

        if (uid == null) return;
        Map<String, Object> update = new HashMap<>();
        update.put(FIELD_ENABLED, config.enabled);
        // Clears the plaintext key earlier versions stored here. Saving the settings
        // once is enough to clean an existing account; without this the old value
        // would sit in Firestore indefinitely even though nothing writes it any more.
        update.put(FIELD_API_KEY, com.google.firebase.firestore.FieldValue.delete());
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .set(update, SetOptions.merge());
    }

    /**
     * Pulls the cloud on/off flag into prefs. Runs {@code onLoaded} either way so the
     * screen can render from whatever it has.
     *
     * <p>The key itself is deliberately not read back. It lives only in encrypted
     * prefs now, so the local value is the only value — and reading the old cloud
     * field would quietly reintroduce the plaintext copy this change removes.
     */
    public static void loadFromFirestore(@NonNull Context context, String uid, Runnable onLoaded) {
        if (uid == null) {
            if (onLoaded != null) onLoaded.run();
            return;
        }
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        SharedPreferencesManager prefs = SharedPreferencesManager.getInstance(context);
                        Boolean enabled = doc.getBoolean(FIELD_ENABLED);
                        if (enabled != null) prefs.setSmsGatewayEnabled(enabled);
                    }
                    if (onLoaded != null) onLoaded.run();
                })
                .addOnFailureListener(e -> {
                    if (onLoaded != null) onLoaded.run();
                });
    }

    /**
     * Masks a key for display: first and last four characters, the rest as dots.
     * Short keys are masked entirely rather than mostly-revealed.
     */
    public static String mask(String apiKey) {
        if (apiKey == null) return "";
        String trimmed = apiKey.trim();
        if (trimmed.isEmpty()) return "";
        if (trimmed.length() <= 12) {
            return repeat(trimmed.length());
        }
        return trimmed.substring(0, 4) + repeat(8) + trimmed.substring(trimmed.length() - 4);
    }

    private static String repeat(int count) {
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < count; i++) dots.append('•');
        return dots.toString();
    }
}
