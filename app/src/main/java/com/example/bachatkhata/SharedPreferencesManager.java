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
    // Budgeting Rule (50/30/20)
    public static final String KEY_MONTHLY_INCOME = "MONTHLY_INCOME";
    public static final String KEY_RULE_NEEDS = "RULE_NEEDS";
    public static final String KEY_RULE_WANTS = "RULE_WANTS";
    public static final String KEY_RULE_INVESTMENTS = "RULE_INVESTMENTS";
    // SMS gateway (Fast2SMS). The API key is a credential — it lives here rather
    // than in the plaintext store precisely because this one is AES-GCM encrypted.
    public static final String KEY_SMS_GATEWAY_ENABLED = "SMS_GATEWAY_ENABLED";
    public static final String KEY_SMS_GATEWAY_API_KEY = "SMS_GATEWAY_API_KEY";
    // Dictation language for voice logging. Separate from the UI language — someone
    // reading the app in English may still speak their transactions in Tamil.
    public static final String KEY_VOICE_LANGUAGE = "VOICE_LANGUAGE";
    // App-lock PIN mirror. Firestore stays the source of truth, but the lock cannot
    // depend on reaching it: a failed read is not proof that no PIN is set, and
    // treating it that way made airplane mode a bypass. Cached here because this
    // store is the AES-GCM encrypted one.
    public static final String KEY_PIN_HASH = "PIN_HASH";
    public static final String KEY_PIN_UID = "PIN_UID";
    public static final String KEY_PIN_KNOWN = "PIN_KNOWN";

    private SharedPreferencesManager(Context context) {
        Context appContext = context.getApplicationContext();
        sharedPreferences = buildPreferences(appContext);
        editor = sharedPreferences.edit();
    }

    /**
     * Build an AES-GCM encrypted preference store, migrating any pre-existing plaintext
     * preferences into it once.
     *
     * <p>A first failure is not treated as "this device cannot encrypt". By far the
     * likeliest cause is a store sealed with a key that no longer exists — a restored
     * backup carries the file but not the AndroidKeyStore key, and the keystore can
     * also be invalidated by a lock-screen change. The file is then permanently
     * unopenable, and the old code answered that by silently returning the legacy
     * plaintext store, which {@link #migrateLegacyPlaintext} had already wiped. The
     * user lost their app-lock settings and SMS gateway key with no error, and every
     * later launch hit the same dead end.
     *
     * <p>So an unopenable store is discarded and rebuilt. That loses the local copy of
     * the settings, which is unavoidable — nothing can decrypt it — but it yields a
     * working store that re-syncs from Firestore, instead of a broken one forever.
     */
    private SharedPreferences buildPreferences(Context appContext) {
        try {
            return createEncrypted(appContext);
        } catch (Exception first) {
            try {
                discardUnreadableStore(appContext);
                return createEncrypted(appContext);
            } catch (Exception second) {
                // Genuinely no encryption available on this device. Last resort, so the
                // app still runs; the PIN's source of truth is Firestore either way.
                return appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            }
        }
    }

    private SharedPreferences createEncrypted(Context appContext) throws Exception {
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
    }

    /**
     * Throws away a store that can no longer be decrypted, along with the master key
     * entry, so the retry above builds a fresh pair. Both halves have to go: keeping
     * the old key would re-seal against a key the file does not match, and keeping the
     * old file would fail against the new key.
     */
    private void discardUnreadableStore(Context appContext) {
        try {
            appContext.deleteSharedPreferences(ENC_PREF_NAME);
        } catch (Exception ignored) {
            // Nothing to delete, or the platform refused — the key reset below may
            // still be enough to recover.
        }
        try {
            java.security.KeyStore keyStore = java.security.KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS);
        } catch (Exception ignored) {
            // No such entry, or the keystore is unavailable. The retry decides.
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

    // ── Budgeting Rule (50/30/20) ──
    // Income is kept as a String so large values keep full precision (SharedPreferences
    // has no double type, and float silently rounds past ~7 significant digits).
    public void setMonthlyIncome(double income) {
        editor.putString(KEY_MONTHLY_INCOME, String.valueOf(Math.max(0, income)));
        editor.apply();
    }

    public double getMonthlyIncome() {
        try {
            return Double.parseDouble(sharedPreferences.getString(KEY_MONTHLY_INCOME, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void setRuleSplit(int needs, int wants, int investments) {
        editor.putInt(KEY_RULE_NEEDS, needs);
        editor.putInt(KEY_RULE_WANTS, wants);
        editor.putInt(KEY_RULE_INVESTMENTS, investments);
        editor.apply();
    }

    public int getRuleNeeds() {
        return sharedPreferences.getInt(KEY_RULE_NEEDS, 50);
    }

    public int getRuleWants() {
        return sharedPreferences.getInt(KEY_RULE_WANTS, 30);
    }

    public int getRuleInvestments() {
        return sharedPreferences.getInt(KEY_RULE_INVESTMENTS, 20);
    }

    // SMS gateway (Fast2SMS)
    public void setSmsGatewayEnabled(boolean enabled) {
        editor.putBoolean(KEY_SMS_GATEWAY_ENABLED, enabled);
        editor.apply();
    }

    public boolean isSmsGatewayEnabled() {
        return sharedPreferences.getBoolean(KEY_SMS_GATEWAY_ENABLED, false);
    }

    public void setSmsGatewayApiKey(String apiKey) {
        editor.putString(KEY_SMS_GATEWAY_API_KEY, apiKey == null ? "" : apiKey);
        editor.apply();
    }

    public String getSmsGatewayApiKey() {
        return sharedPreferences.getString(KEY_SMS_GATEWAY_API_KEY, "");
    }

    // Voice logging dictation language (BCP-47, e.g. "hi-IN")
    public void setVoiceLanguage(String tag) {
        editor.putString(KEY_VOICE_LANGUAGE, tag);
        editor.apply();
    }

    public String getVoiceLanguage() {
        return sharedPreferences.getString(KEY_VOICE_LANGUAGE, VoiceLanguages.DEFAULT_TAG);
    }

    // ── App-lock PIN mirror ──
    //
    // Two distinct facts are stored, and the lock needs both: WHAT the hash is, and
    // WHETHER the answer is known at all. "No PIN set" and "never synced" look
    // identical if only the hash is kept, and they must route differently — the
    // first lets the user straight in, the second must not.

    /**
     * Records the account's PIN state locally. A null or empty {@code hash} is a
     * meaningful value: it records "this account has no PIN", which is exactly what
     * lets the lock skip itself offline without guessing.
     */
    public void setCachedPin(String uid, String hash) {
        if (uid == null || uid.isEmpty()) return;
        editor.putString(KEY_PIN_UID, uid);
        editor.putString(KEY_PIN_HASH, hash == null ? "" : hash);
        editor.putBoolean(KEY_PIN_KNOWN, true);
        editor.apply();
    }

    /**
     * True once {@code uid}'s PIN state has been synced at least once on this device.
     * The uid is part of the check so a cached hash can never be applied to whichever
     * account happens to sign in next.
     */
    public boolean isPinStateKnown(String uid) {
        if (uid == null || uid.isEmpty()) return false;
        if (!sharedPreferences.getBoolean(KEY_PIN_KNOWN, false)) return false;
        return uid.equals(sharedPreferences.getString(KEY_PIN_UID, ""));
    }

    /** The cached hash for {@code uid}; {@code ""} when unknown or when no PIN is set. */
    public String getCachedPinHash(String uid) {
        if (!isPinStateKnown(uid)) return "";
        return sharedPreferences.getString(KEY_PIN_HASH, "");
    }

    /** Drops the mirror — used on sign-out so the next account starts from Firestore. */
    public void clearCachedPin() {
        editor.remove(KEY_PIN_HASH);
        editor.remove(KEY_PIN_UID);
        editor.remove(KEY_PIN_KNOWN);
        editor.apply();
    }

    // Clear settings
    public void clearAll() {
        editor.clear();
        editor.apply();
    }
}
