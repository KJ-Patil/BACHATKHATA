package com.example.bachatkhata;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.example.bachatkhata.databinding.ActivitySplashBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class SplashActivity extends AppCompatActivity {

    private ActivitySplashBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;
    private final Handler routingHandler = new Handler(Looper.getMainLooper());
    private final Runnable routingRunnable = this::routeUser;

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();

        // Delay 2000ms and route
        routingHandler.postDelayed(routingRunnable, 2000);
    }

    private void routeUser() {
        if (isFinishing() || isDestroyed()) return;

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(SplashActivity.this, LoginActivity.class));
            finish();
        } else {
            String uid = currentUser.getUid();
            mFirestore.collection("users").document(uid).get()
                    .addOnCompleteListener(task -> {
                        if (isFinishing() || isDestroyed()) return;
                        
                        if (task.isSuccessful() && task.getResult() != null) {
                            DocumentSnapshot document = task.getResult();
                            if (document.exists()) {
                                // Sync the authoritative theme from Firestore into local prefs and
                                // apply it BEFORE routing. Application.onCreate() only had the local
                                // (possibly stale) value, so without this the app would show the
                                // System default on first launch after login / on a new device until
                                // Profile & Settings was opened.
                                syncTheme(document.getString("themeMode"));

                                String pinHash = document.getString("pinHash");
                                Boolean onboardingComplete = document.getBoolean("onboardingComplete");

                                // Mirror the PIN state locally on every cold start. This is
                                // what lets the lock work — and fail closed — when Firestore
                                // is unreachable on a later launch.
                                SharedPreferencesManager prefs =
                                        SharedPreferencesManager.getInstance(SplashActivity.this);
                                prefs.setCachedPin(uid, pinHash);

                                // One-time default for accounts that already had a PIN. The
                                // idle lock could not be switched on by anything until now,
                                // so those users read as "off" despite having asked for a
                                // lock. Only applied when no deliberate choice exists, so it
                                // never overrides someone who turned it off themselves.
                                if (!prefs.hasChosenAppLock()
                                        && pinHash != null && !pinHash.trim().isEmpty()) {
                                    prefs.setAppLockEnabled(true);
                                }

                                if (onboardingComplete != null && !onboardingComplete) {
                                    startActivity(new Intent(SplashActivity.this, OnboardingActivity.class));
                                } else if (pinHash != null && !pinHash.trim().isEmpty()) {
                                    startLockScreen();
                                } else {
                                    startActivity(new Intent(SplashActivity.this, MainActivity.class));
                                }
                            } else {
                                // Default back to onboarding if user doc doesn't exist yet
                                startActivity(new Intent(SplashActivity.this, OnboardingActivity.class));
                            }
                        } else {
                            // The profile read failed — offline, or the doc is not in the
                            // local cache. Going straight to MainActivity here made airplane
                            // mode a full bypass of the app lock, because the PIN hash lives
                            // in that document. Decide from the local mirror instead.
                            routeFromCachedPin(uid);
                        }
                        finish();
                    });
        }
    }

    private void startLockScreen() {
        Intent intent = new Intent(SplashActivity.this, PinSetupActivity.class);
        intent.putExtra("mode", "VERIFY");
        startActivity(intent);
    }

    /**
     * Routes using the locally mirrored PIN state when Firestore cannot be read.
     *
     * <p>The mirror distinguishes "this account has no PIN" from "never synced", which
     * a bare hash cannot: both look like an empty string, and they have to route
     * differently. A known-empty PIN goes straight in; a known hash gets the lock
     * screen, which verifies against that same cached hash and so works fully offline.
     *
     * <p>The remaining case is an account that has never completed a profile read on
     * this device. Signing in requires the network, and this method runs on every cold
     * start, so it is close to unreachable in practice — but there is genuinely no
     * local evidence either way, so it keeps the previous behaviour rather than
     * locking someone out of an account that may have no PIN at all.
     */
    private void routeFromCachedPin(String uid) {
        SharedPreferencesManager prefs = SharedPreferencesManager.getInstance(this);
        if (prefs.isPinStateKnown(uid) && !prefs.getCachedPinHash(uid).trim().isEmpty()) {
            startLockScreen();
        } else {
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
        }
    }

    /**
     * Persists the Firestore theme locally and applies it if it differs from what is already
     * active. The diff check avoids a needless activity recreation when the local value (already
     * applied in {@link BachatKhataApplication#onCreate()}) is correct.
     */
    private void syncTheme(String themeMode) {
        if (themeMode == null || themeMode.trim().isEmpty()) return;
        SharedPreferencesManager prefs = SharedPreferencesManager.getInstance(this);
        if (!themeMode.equalsIgnoreCase(prefs.getThemeMode())) {
            prefs.setThemeMode(themeMode);
            SharedPreferencesManager.applyThemeMode(themeMode);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        routingHandler.removeCallbacks(routingRunnable);
    }
}
