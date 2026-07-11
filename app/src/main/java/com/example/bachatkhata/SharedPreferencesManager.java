package com.example.bachatkhata;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.util.Map;

public class SharedPreferencesManager {

    private static final String PREF_NAME = "BachatKhata_Prefs";        // legacy plaintext store
    private static final String ENC_PREF_NAME = "BachatKhata_Prefs_Enc"; // AES-encrypted store
    private static final String KEY_MIGRATED_TO_ENC = "MIGRATED_TO_ENC";
    private static SharedPreferencesManager instance;
    private final SharedPreferences sharedPreferences;
    private final SharedPreferences.Editor editor;

    // Required Keys
    public static final String KEY_APP_LOCK_ENABLED = "APP_LOCK_ENABLED";
    public static final String KEY_LAST_PAUSED_TIME = "LAST_PAUSED_TIME";
    public static final String KEY_SELECTED_PERIOD = "SELECTED_PERIOD";
    public static final String KEY_ONBOARDING_SHOWN = "ONBOARDING_SHOWN";
    public static final String KEY_USER_UID = "USER_UID";
    public static final String KEY_USER_CURRENCY = "USER_CURRENCY";
    public static final String KEY_USER_CURRENCY_SYMBOL = "USER_CURRENCY_SYMBOL";
    public static final String KEY_BIOMETRIC_ENABLED = "BIOMETRIC_ENABLED";
    public static final String KEY_LOCK_TIMEOUT_SECONDS = "LOCK_TIMEOUT_SECONDS";
    public static final String KEY_ROUNDUP_ENABLED = "ROUNDUP_ENABLED";
    public static final String KEY_ROUNDUP_LIMIT = "ROUNDUP_LIMIT";
    public static final String KEY_THEME_MODE = "THEME_MODE";
    public static final String KEY_REMEMBER_ME = "REMEMBER_ME";
    public static final String KEY_REMEMBERED_EMAIL = "REMEMBERED_EMAIL";

    private SharedPreferencesManager(Context context) {
        Context appContext = context.getApplicationContext();
        sharedPreferences = buildPreferences(appContext);
        editor = sharedPreferences.edit();
    }

    /**
     * Build an AES-GCM encrypted preference store, migrating any pre-existing plaintext
     * preferences into it once. Falls back to the plaintext store if the device cannot
     * provide encryption (keeps the app usable rather than crashing).
     */
    private SharedPreferences buildPreferences(Context appContext) {
        try {
            MasterKey masterKey = new MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            SharedPreferences encrypted = EncryptedSharedPreferences.create(
                    appContext,
                    ENC_PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);

            migrateLegacyPlaintext(appContext, encrypted);
            return encrypted;
        } catch (Exception e) {
            // Encryption unavailable — degrade gracefully to the legacy plaintext store.
            return appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        }
    }

    @SuppressWarnings("unchecked")
    private void migrateLegacyPlaintext(Context appContext, SharedPreferences encrypted) {
        if (encrypted.getBoolean(KEY_MIGRATED_TO_ENC, false)) return;

        SharedPreferences legacy = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Map<String, ?> all = legacy.getAll();
        SharedPreferences.Editor encEditor = encrypted.edit();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof String) {
                encEditor.putString(key, (String) value);
            } else if (value instanceof Boolean) {
                encEditor.putBoolean(key, (Boolean) value);
            } else if (value instanceof Integer) {
                encEditor.putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                encEditor.putLong(key, (Long) value);
            } else if (value instanceof Float) {
                encEditor.putFloat(key, (Float) value);
            }
        }
        encEditor.putBoolean(KEY_MIGRATED_TO_ENC, true);
        encEditor.apply();

        // Wipe the now-redundant plaintext copy.
        legacy.edit().clear().apply();
    }

    public static synchronized SharedPreferencesManager getInstance(Context context) {
        if (instance == null) {
            instance = new SharedPreferencesManager(context);
        }
        return instance;
    }

    // Remember Me (login email pre-fill)
    public void setRememberMe(boolean remember, String email) {
        editor.putBoolean(KEY_REMEMBER_ME, remember);
        editor.putString(KEY_REMEMBERED_EMAIL, remember ? email : "");
        editor.apply();
    }

    public boolean isRememberMe() {
        return sharedPreferences.getBoolean(KEY_REMEMBER_ME, false);
    }

    public String getRememberedEmail() {
        return sharedPreferences.getString(KEY_REMEMBERED_EMAIL, "");
    }

    // Round-up Settings
    public void setRoundUpEnabled(boolean enabled) {
        editor.putBoolean(KEY_ROUNDUP_ENABLED, enabled);
        editor.apply();
    }

    public boolean isRoundUpEnabled() {
        return sharedPreferences.getBoolean(KEY_ROUNDUP_ENABLED, false);
    }

    public void setRoundUpLimit(int limit) {
        editor.putInt(KEY_ROUNDUP_LIMIT, limit);
        editor.apply();
    }

    public int getRoundUpLimit() {
        return sharedPreferences.getInt(KEY_ROUNDUP_LIMIT, 10); // Default ₹10 rounding
    }

    // App Lock Enabled
    public void setAppLockEnabled(boolean enabled) {
        editor.putBoolean(KEY_APP_LOCK_ENABLED, enabled);
        editor.apply();
    }

    public boolean isAppLockEnabled() {
        return sharedPreferences.getBoolean(KEY_APP_LOCK_ENABLED, false);
    }

    // Last Paused Time (for timeout tracking)
    public void setLastPausedTime(long timeMs) {
        editor.putLong(KEY_LAST_PAUSED_TIME, timeMs);
        editor.apply();
    }

    public long getLastPausedTime() {
        return sharedPreferences.getLong(KEY_LAST_PAUSED_TIME, -1);
    }

    // Selected Period
    public void setSelectedPeriod(int period) {
        editor.putInt(KEY_SELECTED_PERIOD, period);
        editor.apply();
    }

    public int getSelectedPeriod() {
        return sharedPreferences.getInt(KEY_SELECTED_PERIOD, 0); // 0 = This Month
    }

    // Onboarding Shown
    public void setOnboardingShown(boolean shown) {
        editor.putBoolean(KEY_ONBOARDING_SHOWN, shown);
        editor.apply();
    }

    public boolean isOnboardingShown() {
        return sharedPreferences.getBoolean(KEY_ONBOARDING_SHOWN, false);
    }

    // User UID
    public void setUserUid(String uid) {
        editor.putString(KEY_USER_UID, uid);
        editor.apply();
    }

    public String getUserUid() {
        return sharedPreferences.getString(KEY_USER_UID, "");
    }

    // User Currency
    public void setUserCurrency(String currencyCode) {
        editor.putString(KEY_USER_CURRENCY, currencyCode);
        editor.apply();
    }

    public String getUserCurrency() {
        return sharedPreferences.getString(KEY_USER_CURRENCY, "INR");
    }

    // User Currency Symbol
    public void setUserCurrencySymbol(String symbol) {
        editor.putString(KEY_USER_CURRENCY_SYMBOL, symbol);
        editor.apply();
    }

    public String getUserCurrencySymbol() {
        return sharedPreferences.getString(KEY_USER_CURRENCY_SYMBOL, "₹");
    }

    // Biometric Enabled
    public void setBiometricEnabled(boolean enabled) {
        editor.putBoolean(KEY_BIOMETRIC_ENABLED, enabled);
        editor.apply();
    }

    public boolean isBiometricEnabled() {
        return sharedPreferences.getBoolean(KEY_BIOMETRIC_ENABLED, false);
    }

    // Lock Timeout Seconds
    public void setLockTimeoutSeconds(int seconds) {
        editor.putInt(KEY_LOCK_TIMEOUT_SECONDS, seconds);
        editor.apply();
    }

    public int getLockTimeoutSeconds() {
        return sharedPreferences.getInt(KEY_LOCK_TIMEOUT_SECONDS, 60); // default 60s
    }

    // Theme Mode ("System" | "Light" | "Dark")
    public void setThemeMode(String mode) {
        editor.putString(KEY_THEME_MODE, mode);
        editor.apply();
    }

    public String getThemeMode() {
        return sharedPreferences.getString(KEY_THEME_MODE, "System"); // default follow system
    }

    // Map a theme mode string to AppCompatDelegate night mode and apply it
    public static void applyThemeMode(String mode) {
        if ("Dark".equalsIgnoreCase(mode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else if ("Light".equalsIgnoreCase(mode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    // Clear settings
    public void clearAll() {
        editor.clear();
        editor.apply();
    }
}
