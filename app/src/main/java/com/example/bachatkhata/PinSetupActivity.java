package com.example.bachatkhata;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.example.bachatkhata.databinding.ActivityPinSetupBinding;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.concurrent.Executor;

public class PinSetupActivity extends AppCompatActivity {

    private ActivityPinSetupBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;

    private String mode = "SETUP"; // "SETUP" or "VERIFY"
    private String correctPinHash = "";
    private String tempPin = "";
    private String enteredPin = "";
    private boolean isConfirming = false;
    private boolean isBiometricEnabled = false;

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPinSetupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        BaseActivity.applyEdgeToEdgeInsets(findViewById(android.R.id.content));

        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();

        if (getIntent().hasExtra("mode")) {
            mode = getIntent().getStringExtra("mode");
        }

        setupUI();
        setupListeners();
        loadPinAndBiometricStatus();
    }

    private void setupUI() {
        if ("VERIFY".equals(mode)) {
            binding.txtPinTitle.setText(getString(R.string.pin_enter_title));
            binding.btnKeyFingerprint.setVisibility(View.VISIBLE);
        } else {
            binding.txtPinTitle.setText(getString(R.string.pin_create_title));
            binding.btnKeyFingerprint.setVisibility(View.GONE);
        }
    }

    private void loadPinAndBiometricStatus() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        showLoading(true);
        mFirestore.collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    showLoading(false);
                    if (documentSnapshot.exists()) {
                        String pinHash = documentSnapshot.getString("pinHash");
                        Boolean bioEnabled = documentSnapshot.getBoolean("biometricEnabled");

                        if (pinHash != null) {
                            correctPinHash = pinHash;
                        }
                        if (bioEnabled != null) {
                            isBiometricEnabled = bioEnabled;
                        }

                        // Auto-prompt fingerprint if verify mode & biometric enabled
                        if ("VERIFY".equals(mode) && isBiometricEnabled) {
                            binding.btnKeyFingerprint.setVisibility(View.VISIBLE);
                            promptBiometrics();
                        } else {
                            binding.btnKeyFingerprint.setVisibility(View.GONE);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    // If offline, continue without remote verification check (will fail PIN matching unless cached)
                });
    }

    private void setupListeners() {
        // Numpad Numbers
        binding.btnKey0.setOnClickListener(v -> appendDigit("0"));
        binding.btnKey1.setOnClickListener(v -> appendDigit("1"));
        binding.btnKey2.setOnClickListener(v -> appendDigit("2"));
        binding.btnKey3.setOnClickListener(v -> appendDigit("3"));
        binding.btnKey4.setOnClickListener(v -> appendDigit("4"));
        binding.btnKey5.setOnClickListener(v -> appendDigit("5"));
        binding.btnKey6.setOnClickListener(v -> appendDigit("6"));
        binding.btnKey7.setOnClickListener(v -> appendDigit("7"));
        binding.btnKey8.setOnClickListener(v -> appendDigit("8"));
        binding.btnKey9.setOnClickListener(v -> appendDigit("9"));

        // Backspace
        binding.btnKeyBackspace.setOnClickListener(v -> {
            if (enteredPin.length() > 0) {
                enteredPin = enteredPin.substring(0, enteredPin.length() - 1);
                updateDots();
            }
        });

        // Fingerprint
        binding.btnKeyFingerprint.setOnClickListener(v -> {
            if (isBiometricEnabled) {
                promptBiometrics();
            } else {
                Snackbar.make(binding.getRoot(), "Biometrics not enabled on profile.", Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    private void appendDigit(String digit) {
        if (enteredPin.length() < 4) {
            enteredPin += digit;
            updateDots();

            if (enteredPin.length() == 4) {
                // Trigger logic after entering 4 digits
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::processPinEntry, 200);
            }
        }
    }

    private void updateDots() {
        int length = enteredPin.length();
        setDotFilled(binding.dot1, length >= 1);
        setDotFilled(binding.dot2, length >= 2);
        setDotFilled(binding.dot3, length >= 3);
        setDotFilled(binding.dot4, length >= 4);
    }

    private void setDotFilled(ImageView dot, boolean filled) {
        dot.setImageResource(filled ? R.drawable.dot_pin_filled : R.drawable.dot_pin_empty);
    }

    private void processPinEntry() {
        if ("SETUP".equals(mode)) {
            if (!isConfirming) {
                // First step of PIN setup
                tempPin = enteredPin;
                enteredPin = "";
                isConfirming = true;
                updateDots();
                binding.txtPinTitle.setText(getString(R.string.pin_confirm_title));
            } else {
                // Confirmation step of PIN setup
                if (enteredPin.equals(tempPin)) {
                    String hashed = hashPinV2(enteredPin);
                    savePinToFirestore(hashed);
                } else {
                    shakeDots();
                    showError(getString(R.string.pin_setup_mismatch));
                    // Reset to initial create state
                    tempPin = "";
                    enteredPin = "";
                    isConfirming = false;
                    updateDots();
                    binding.txtPinTitle.setText(getString(R.string.pin_create_title));
                }
            }
        } else {
            // VERIFY Mode
            if (verifyPin(enteredPin, correctPinHash)) {
                // Transparently upgrade legacy unsalted hashes to the salted v2$ format.
                if (!correctPinHash.startsWith("v2$")) {
                    savePinHashSilently(hashPinV2(enteredPin));
                }
                BaseActivity.setAppUnlocked();
                startActivity(new Intent(PinSetupActivity.this, MainActivity.class)
                        .putExtra(MainActivity.EXTRA_SKIP_BIOMETRIC, true));
                finish();
            } else {
                shakeDots();
                showError(getString(R.string.pin_incorrect));
                enteredPin = "";
                updateDots();
            }
        }
    }

    private void savePinToFirestore(String hash) {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        showLoading(true);
        mFirestore.collection("users").document(uid)
                .set(Collections.singletonMap("pinHash", hash), SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    // Successfully saved, go to BiometricSetupActivity
                    startActivity(new Intent(PinSetupActivity.this, BiometricSetupActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    showError("Failed to configure PIN lock: " + e.getMessage());
                    enteredPin = "";
                    isConfirming = false;
                    updateDots();
                    binding.txtPinTitle.setText(getString(R.string.pin_create_title));
                });
    }

    private void promptBiometrics() {
        BiometricManager biometricManager = BiometricManager.from(this);
        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
            Executor executor = ContextCompat.getMainExecutor(this);
            BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor,
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                            super.onAuthenticationError(errorCode, errString);
                        }

                        @Override
                        public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                            super.onAuthenticationSucceeded(result);
                            BaseActivity.setAppUnlocked();
                            startActivity(new Intent(PinSetupActivity.this, MainActivity.class)
                                    .putExtra(MainActivity.EXTRA_SKIP_BIOMETRIC, true));
                            finish();
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

    private void shakeDots() {
        TranslateAnimation shake = new TranslateAnimation(0, 10, 0, 0);
        shake.setDuration(50);
        shake.setRepeatMode(Animation.REVERSE);
        shake.setRepeatCount(5);
        binding.layoutDots.startAnimation(shake);
    }

    /** Produce a salted PIN hash in the {@code v2$<saltHex>$<hashHex>} format. */
    private String hashPinV2(String pin) {
        byte[] salt = new byte[16];
        new java.security.SecureRandom().nextBytes(salt);
        String saltHex = toHex(salt);
        return "v2$" + saltHex + "$" + sha256(saltHex + pin);
    }

    /** Verify a PIN against a stored hash, supporting both salted v2$ and legacy unsalted hashes. */
    private boolean verifyPin(String pin, String storedHash) {
        if (storedHash == null || storedHash.isEmpty()) return false;
        if (storedHash.startsWith("v2$")) {
            String[] parts = storedHash.split("\\$");
            if (parts.length != 3) return false;
            String saltHex = parts[1];
            String expected = parts[2];
            return sha256(saltHex + pin).equals(expected);
        }
        // Legacy: unsalted SHA-256 of the raw PIN.
        return sha256(pin).equals(storedHash);
    }

    /** Persist an upgraded PIN hash without navigating away (used for silent legacy upgrades). */
    private void savePinHashSilently(String hash) {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();
        mFirestore.collection("users").document(uid)
                .set(Collections.singletonMap("pinHash", hash), SetOptions.merge());
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        return sb.toString();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private void showLoading(boolean isLoading) {
        binding.loaderOverlay.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        Snackbar snackbar = Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT);
        snackbar.setBackgroundTint(getResources().getColor(R.color.colorDanger));
        snackbar.show();
    }
}
