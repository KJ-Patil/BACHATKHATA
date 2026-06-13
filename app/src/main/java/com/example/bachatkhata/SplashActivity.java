package com.example.bachatkhata;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.DecelerateInterpolator;

import androidx.appcompat.app.AppCompatActivity;

import com.example.bachatkhata.databinding.ActivitySplashBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class SplashActivity extends AppCompatActivity {

    private ActivitySplashBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();

        // 1. Setup entry animation for Icon, Title and Tagline
        binding.lottieSplash.setScaleX(0f);
        binding.lottieSplash.setScaleY(0f);
        binding.lottieSplash.setAlpha(0f);

        binding.txtAppName.setTranslationY(30f);
        binding.txtAppName.setAlpha(0f);
        binding.txtTagline.setTranslationY(30f);
        binding.txtTagline.setAlpha(0f);

        binding.lottieSplash.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(500)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        binding.txtAppName.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(300)
                .setStartDelay(300)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        binding.txtTagline.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(300)
                .setStartDelay(500)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // 2. Wait 2000ms and route user
        new Handler(Looper.getMainLooper()).postDelayed(this::routeUser, 2000);
    }

    private void routeUser() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(SplashActivity.this, LoginActivity.class));
            finish();
        } else {
            String uid = currentUser.getUid();
            mFirestore.collection("users").document(uid).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String pinHash = documentSnapshot.getString("pinHash");
                            Boolean onboardingComplete = documentSnapshot.getBoolean("onboardingComplete");
                            
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
                            // No profile exists yet, send to register/onboarding
                            startActivity(new Intent(SplashActivity.this, OnboardingActivity.class));
                        }
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        // Offline or network error: fallback to main screen or PIN setup if cached
                        startActivity(new Intent(SplashActivity.this, MainActivity.class));
                        finish();
                    });
        }
    }
}
