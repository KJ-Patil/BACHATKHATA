package com.example.bachatkhata;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class BaseActivity extends AppCompatActivity {

    private static int activeActivitiesCount = 0;
    private static long backgroundTimeMs = -1;
    private static boolean isAppLocked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // If returned from background
        if (activeActivitiesCount == 0 && backgroundTimeMs != -1) {
            long idleTime = System.currentTimeMillis() - backgroundTimeMs;
            if (idleTime > 60000) { // backgrounded for > 60s
                isAppLocked = true;
            }
        }

        activeActivitiesCount++;
        backgroundTimeMs = -1; // Reset background time

        // Check if lock should be enforced
        if (isAppLocked && shouldEnforceLock()) {
            checkPinConfigAndLock();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        activeActivitiesCount--;

        if (activeActivitiesCount == 0) {
            // App entered background
            backgroundTimeMs = System.currentTimeMillis();
        }
    }

    private boolean shouldEnforceLock() {
        // Exempt auth/setup activities from locking
        String className = getClass().getSimpleName();
        return !className.equals("PinSetupActivity")
                && !className.equals("SplashActivity")
                && !className.equals("LoginActivity")
                && !className.equals("RegisterActivity")
                && !className.equals("ForgotPasswordActivity")
                && !className.equals("OnboardingActivity")
                && !className.equals("BiometricSetupActivity");
    }

    private void checkPinConfigAndLock() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            // No user is logged in, no need to lock
            isAppLocked = false;
            return;
        }

        String uid = auth.getCurrentUser().getUid();

        // Fetch user from Firestore to see if they actually have a PIN configured
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String pinHash = documentSnapshot.getString("pinHash");
                        if (pinHash != null && !pinHash.trim().isEmpty()) {
                            // PIN is configured, launch verification activity
                            Intent intent = new Intent(BaseActivity.this, PinSetupActivity.class);
                            intent.putExtra("mode", "VERIFY");
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        } else {
                            // PIN not set up, unlock
                            isAppLocked = false;
                        }
                    } else {
                        isAppLocked = false;
                    }
                })
                .addOnFailureListener(e -> {
                    // Fallback to unlock on failure so we don't lock user out
                    isAppLocked = false;
                });
    }

    public static void setAppUnlocked() {
        isAppLocked = false;
        backgroundTimeMs = -1;
    }
}
