package com.example.bachatkhata;

import android.app.Application;
import com.google.firebase.FirebaseApp;
import androidx.appcompat.app.AppCompatDelegate;

public class BachatKhataApplication extends Application {

    private static BachatKhataApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this);
        
        // Set Theme Mode to Follow System default
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    public static synchronized BachatKhataApplication getInstance() {
        return instance;
    }
}
