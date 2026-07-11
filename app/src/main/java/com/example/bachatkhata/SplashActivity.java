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
                                String pinHash = document.getString("pinHash");
                                Boolean onboardingComplete = document.getBoolean("onboardingComplete");

                                if (onboardingComplete != null && !onboardingComplete) {
                                    startActivity(new Intent(SplashActivity.this, OnboardingActivity.class));
                                } else if (pinHash != null && !pinHash.trim().isEmpty()) {
                                    Intent intent = new Intent(SplashActivity.this, PinSetupActivity.class);
                                    intent.putExtra("mode", "VERIFY");
                                    startActivity(intent);
                                } else {
                                    startActivity(new Intent(SplashActivity.this, MainActivity.class));
                                }
                            } else {
                                // Default back to onboarding if user doc doesn't exist yet
                                startActivity(new Intent(SplashActivity.this, OnboardingActivity.class));
                            }
                        } else {
                            // Fallback to MainActivity if network fails or doc reading fails
                            startActivity(new Intent(SplashActivity.this, MainActivity.class));
                        }
                        finish();
                    });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        routingHandler.removeCallbacks(routingRunnable);
    }
}
