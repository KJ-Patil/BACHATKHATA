package com.example.bachatkhata;

import android.app.Application;

import androidx.emoji2.bundled.BundledEmojiCompatConfig;
import androidx.emoji2.text.EmojiCompat;

import com.google.firebase.FirebaseApp;

public class BachatKhataApplication extends Application {

    private static BachatKhataApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // Use the color emoji font bundled in the APK and replace every emoji
        // (even ones the system font "supports") so tiles never fall back to
        // the black-and-white outline glyphs on emulators / older devices.
        EmojiCompat.init(new BundledEmojiCompatConfig(this).setReplaceAll(true));

        // Initialize Firebase
        FirebaseApp.initializeApp(this);

        // Load cached exchange rates and refresh them in the background if stale
        CurrencyManager.getInstance().init(this);
        
        // Apply the user's saved theme mode (defaults to "System" if unset)
        SharedPreferencesManager.applyThemeMode(
                SharedPreferencesManager.getInstance(this).getThemeMode());
    }

    public static synchronized BachatKhataApplication getInstance() {
        return instance;
    }
}
