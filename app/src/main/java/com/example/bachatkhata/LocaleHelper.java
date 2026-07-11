package com.example.bachatkhata;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import java.util.Locale;

/**
 * Applies the user's chosen UI language by wrapping each activity's base context with a
 * localized {@link Configuration}. The choice is stored in a small plaintext preference file
 * (language is not sensitive) so it can be read very early in {@code attachBaseContext} without
 * touching the encrypted store.
 */
public final class LocaleHelper {

    private static final String PREF = "BachatKhata_Locale";
    private static final String KEY_LANG = "app_language";
    public static final String DEFAULT_LANGUAGE = "en";

    // Supported UI languages: English, Hindi, Marathi.
    public static final String[] SUPPORTED_CODES = {"en", "hi", "mr"};
    public static final String[] SUPPORTED_NAMES = {"English", "हिंदी (Hindi)", "मराठी (Marathi)"};

    private LocaleHelper() {}

    public static void setLanguage(Context context, String code) {
        prefs(context).edit().putString(KEY_LANG, code).apply();
    }

    public static String getLanguage(Context context) {
        return prefs(context).getString(KEY_LANG, DEFAULT_LANGUAGE);
    }

    public static String displayNameFor(String code) {
        for (int i = 0; i < SUPPORTED_CODES.length; i++) {
            if (SUPPORTED_CODES[i].equals(code)) return SUPPORTED_NAMES[i];
        }
        return SUPPORTED_NAMES[0];
    }

    /** Wrap a context so its resources resolve against the selected language. */
    public static Context wrap(Context context) {
        String lang = getLanguage(context);
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);

        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);
        return context.createConfigurationContext(config);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
