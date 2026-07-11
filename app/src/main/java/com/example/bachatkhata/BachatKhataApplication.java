package com.example.bachatkhata;

import android.app.Application;
import com.google.firebase.FirebaseApp;

public class BachatKhataApplication extends Application {

    private static BachatKhataApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        
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
