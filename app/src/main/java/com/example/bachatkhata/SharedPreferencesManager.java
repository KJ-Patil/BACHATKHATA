package com.example.bachatkhata;

import android.content.Context;
import android.content.SharedPreferences;

public class SharedPreferencesManager {

    private static final String PREF_NAME = "BachatKhata_Prefs";
    private static SharedPreferencesManager instance;
    private final SharedPreferences sharedPreferences;
    private final SharedPreferences.Editor editor;

    // Pref Keys
    private static final String KEY_ONBOARDING_COMPLETE = "onboarding_complete";
    private static final String KEY_USER_NAME = "cached_user_name";
    private static final String KEY_USER_EMAIL = "cached_user_email";
    private static final String KEY_BIOMETRIC_PREF = "cached_biometric_enabled";

    private SharedPreferencesManager(Context context) {
        sharedPreferences = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
    }

    public static synchronized SharedPreferencesManager getInstance(Context context) {
        if (instance == null) {
            instance = new SharedPreferencesManager(context);
        }
        return instance;
    }

    public void setOnboardingComplete(boolean complete) {
        editor.putBoolean(KEY_ONBOARDING_COMPLETE, complete);
        editor.apply();
    }

    public boolean isOnboardingComplete() {
        return sharedPreferences.getBoolean(KEY_ONBOARDING_COMPLETE, false);
    }

    public void setCachedUserName(String name) {
        editor.putString(KEY_USER_NAME, name);
        editor.apply();
    }

    public String getCachedUserName() {
        return sharedPreferences.getString(KEY_USER_NAME, "");
    }

    public void setCachedUserEmail(String email) {
        editor.putString(KEY_USER_EMAIL, email);
        editor.apply();
    }

    public String getCachedUserEmail() {
        return sharedPreferences.getString(KEY_USER_EMAIL, "");
    }

    public void setBiometricEnabled(boolean enabled) {
        editor.putBoolean(KEY_BIOMETRIC_PREF, enabled);
        editor.apply();
    }

    public boolean isBiometricEnabled() {
        return sharedPreferences.getBoolean(KEY_BIOMETRIC_PREF, false);
    }

    public void clearAll() {
        editor.clear();
        editor.apply();
    }
}
