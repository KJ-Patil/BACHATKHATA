package com.example.bachatkhata;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.bachatkhata.databinding.ActivityMainBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.concurrent.Executor;

public class MainActivity extends BaseActivity {

    private ActivityMainBinding binding;
    private NavController navController;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;

    private boolean isBiometricEnabled = false;
    private boolean isBiometricPromptShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();

        // 1. Setup Navigation Component
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(binding.bottomNavigationView, navController);
        }

        // 2. Setup FAB Click and Entry Animation
        binding.fabAdd.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, AddTransactionActivity.class));
        });
        animateFABEntry();

        // 3. Request Notifications Permission (Android 13+)
        requestNotificationPermission();

        // 4. Load Currency configuration and observe network state
        if (mAuth.getCurrentUser() != null) {
            CurrencyManager.getInstance().loadFromFirestore(mAuth.getCurrentUser().getUid(), null);
            loadUserPreferences();
        }

        NetworkStateManager.getInstance(this).getIsOnline().observe(this, isOnline -> {
            if (isOnline) {
                binding.txtOfflineBanner.setVisibility(View.GONE);
            } else {
                binding.txtOfflineBanner.setVisibility(View.VISIBLE);
            }
        });

        // 5. Schedule Background Budget Alerts Periodic Task
        scheduleBudgetAlerts();
    }

    private void scheduleBudgetAlerts() {
        androidx.work.PeriodicWorkRequest budgetCheckRequest =
                new androidx.work.PeriodicWorkRequest.Builder(
                        BudgetAlertWorker.class,
                        24, java.util.concurrent.TimeUnit.HOURS)
                        .build();

        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "BudgetAlertCheck",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                budgetCheckRequest
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Prompt biometrics on resume if enabled
        if (isBiometricEnabled && !isBiometricPromptShown) {
            promptBiometrics();
        }
    }

    private void animateFABEntry() {
        binding.fabAdd.setScaleX(0f);
        binding.fabAdd.setScaleY(0f);
        binding.fabAdd.setRotation(0f);

        binding.fabAdd.animate()
                .scaleX(1f)
                .scaleY(1f)
                .rotation(360f)
                .setDuration(500)
                .setInterpolator(new OvershootInterpolator())
                .start();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    private void loadUserPreferences() {
        if (mAuth.getCurrentUser() == null) return;
        mFirestore.collection("users").document(mAuth.getCurrentUser().getUid()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Boolean bioEnabled = documentSnapshot.getBoolean("biometricEnabled");
                        if (bioEnabled != null) {
                            isBiometricEnabled = bioEnabled;
                            // Prompt immediately on initial load if enabled
                            if (isBiometricEnabled && !isBiometricPromptShown) {
                                promptBiometrics();
                            }
                        }
                    }
                });
    }

    private void promptBiometrics() {
        BiometricManager biometricManager = BiometricManager.from(this);
        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                == BiometricManager.BIOMETRIC_SUCCESS) {

            isBiometricPromptShown = true;
            Executor executor = ContextCompat.getMainExecutor(this);
            BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor,
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                            super.onAuthenticationError(errorCode, errString);
                            // Fallback to PIN lock screen on error or user cancel
                            isBiometricPromptShown = false;
                            Intent intent = new Intent(MainActivity.this, PinSetupActivity.class);
                            intent.putExtra("mode", "VERIFY");
                            startActivity(intent);
                        }

                        @Override
                        public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                            super.onAuthenticationSucceeded(result);
                            isBiometricPromptShown = false; // reset for next backgrounding
                        }

                        @Override
                        public void onAuthenticationFailed() {
                            super.onAuthenticationFailed();
                        }
                    });

            BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(getString(R.string.biometric_auth_title))
                    .setSubtitle(getString(R.string.biometric_auth_subtitle))
                    .setNegativeButtonText(getString(R.string.cancel))
                    .build();

            biometricPrompt.authenticate(promptInfo);
        }
    }
}