package com.example.bachatkhata;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.example.bachatkhata.databinding.ActivityBiometricSetupBinding;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.concurrent.Executor;

public class BiometricSetupActivity extends AppCompatActivity {

    private ActivityBiometricSetupBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBiometricSetupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();

        checkBiometricAvailability();

        binding.btnEnableBio.setOnClickListener(v -> setupBiometrics());
        binding.btnSkipBio.setOnClickListener(v -> skipBiometrics());
    }

    private void checkBiometricAvailability() {
        BiometricManager biometricManager = BiometricManager.from(this);
        int canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            // Biometrics not available (not enrolled, not supported, etc.)
            Toast.makeText(this, "Biometric unlock not available on this device.", Toast.LENGTH_LONG).show();
            navigateToMain();
        }
    }

    private void setupBiometrics() {
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        showError("Biometric Setup Error: " + errString);
                    }

                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        saveBiometricSetting(true);
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        showError("Fingerprint verification failed.");
                    }
                });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Confirm Fingerprint")
                .setSubtitle("Scan your fingerprint to enable biometric login")
                .setNegativeButtonText(getString(R.string.cancel))
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void saveBiometricSetting(boolean enabled) {
        if (mAuth.getCurrentUser() == null) {
            navigateToMain();
            return;
        }

        showLoading(true);
        String uid = mAuth.getCurrentUser().getUid();

        mFirestore.collection("users").document(uid)
                .update("biometricEnabled", enabled)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    navigateToMain();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    showError("Failed to update profile: " + e.getLocalizedMessage());
                });
    }

    private void skipBiometrics() {
        saveBiometricSetting(false);
    }

    private void navigateToMain() {
        startActivity(new Intent(BiometricSetupActivity.this, MainActivity.class));
        finish();
    }

    private void showLoading(boolean isLoading) {
        binding.loaderOverlay.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT).show();
    }
}
