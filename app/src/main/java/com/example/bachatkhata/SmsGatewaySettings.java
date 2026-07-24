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
 * <p>Prefs are the source of truth for reads (and are AES-GCM encrypted at rest
 * via {@link SharedPreferencesManager}); Firestore mirrors to the user's own
 * account so the key follows them to a new device.
 */
public final class SmsGatewaySettings {

    private static final String FIELD_ENABLED = "smsGatewayEnabled";
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
     * Saves the config. Each field is written explicitly rather than merged from a
     * spread, so a partial cloud document can never leave one of them undefined.
     */
    public static void save(@NonNull Context context, String uid, Config config) {
        SharedPreferencesManager prefs = SharedPreferencesManager.getInstance(context);
        prefs.setSmsGatewayEnabled(config.enabled);
        prefs.setSmsGatewayApiKey(config.apiKey.trim());

        if (uid == null) return;
        Map<String, Object> update = new HashMap<>();
        update.put(FIELD_ENABLED, config.enabled);
        update.put(FIELD_API_KEY, config.apiKey.trim());
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .set(update, SetOptions.merge());
    }

    /**
     * Pulls the cloud copy into prefs. Runs {@code onLoaded} either way so the
     * screen can render from whatever it has — being offline should show the last
     * known key, not an empty form whose Save would wipe the stored one.
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

                        String apiKey = doc.getString(FIELD_API_KEY);
                        // Only overwrite with a real value: a missing field must not
                        // clear a key the user already has stored locally.
                        if (apiKey != null) prefs.setSmsGatewayApiKey(apiKey);
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
