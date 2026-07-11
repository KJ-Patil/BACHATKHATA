package com.example.bachatkhata;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bachatkhata.databinding.ActivityPhoneLoginBinding;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Phone-number + SMS OTP sign-in using Firebase {@link PhoneAuthProvider}.
 *
 * NOTE: Requires the "Phone" sign-in provider to be enabled in the Firebase console and the
 * app's SHA-1/SHA-256 fingerprints registered (for reCAPTCHA/Play Integrity verification).
 * Without that console setup the SMS will not be delivered — the code path here is complete.
 */
public class PhoneLoginActivity extends AppCompatActivity {

    private ActivityPhoneLoginBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;
    private String verificationId;

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPhoneLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        BaseActivity.applyEdgeToEdgeInsets(findViewById(android.R.id.content));

        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnSendOtp.setOnClickListener(v -> sendOtp());
        binding.btnVerifyOtp.setOnClickListener(v -> verifyOtp());
    }

    private void sendOtp() {
        String phone = binding.etPhone.getText().toString().trim();
        if (!phone.startsWith("+") || phone.length() < 8) {
            binding.tilPhone.setError(getString(R.string.phone_enter_valid));
            return;
        }
        binding.tilPhone.setError(null);
        showLoading(true);

        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(mAuth)
                .setPhoneNumber(phone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(callbacks)
                .build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private final PhoneAuthProvider.OnVerificationStateChangedCallbacks callbacks =
            new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                @Override
                public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                    // Auto-retrieval or instant validation — sign in directly.
                    signInWithCredential(credential);
                }

                @Override
                public void onVerificationFailed(@NonNull FirebaseException e) {
                    showLoading(false);
                    showError(e.getLocalizedMessage());
                }

                @Override
                public void onCodeSent(@NonNull String id,
                                       @NonNull PhoneAuthProvider.ForceResendingToken token) {
                    showLoading(false);
                    verificationId = id;
                    binding.tilOtp.setVisibility(View.VISIBLE);
                    binding.btnVerifyOtp.setVisibility(View.VISIBLE);
                    Snackbar.make(binding.getRoot(), R.string.phone_otp_sent, Snackbar.LENGTH_SHORT).show();
                }
            };

    private void verifyOtp() {
        String code = binding.etOtp.getText().toString().trim();
        if (code.length() != 6 || verificationId == null) {
            binding.tilOtp.setError(getString(R.string.phone_enter_code));
            return;
        }
        binding.tilOtp.setError(null);
        showLoading(true);
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
        signInWithCredential(credential);
    }

    private void signInWithCredential(PhoneAuthCredential credential) {
        mAuth.signInWithCredential(credential)
                .addOnSuccessListener(result -> ensureUserDocAndRoute())
                .addOnFailureListener(e -> {
                    showLoading(false);
                    showError(e.getLocalizedMessage());
                });
    }

    /** Create a minimal profile for first-time phone users, then route like the main login. */
    private void ensureUserDocAndRoute() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) { showLoading(false); return; }
        String uid = user.getUid();

        mFirestore.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        routeExisting(doc.getString("pinHash"), doc.getBoolean("onboardingComplete"));
                    } else {
                        Map<String, Object> userData = new HashMap<>();
                        userData.put("name", "Phone User");
                        userData.put("phone", user.getPhoneNumber());
                        userData.put("currency", "INR");
                        userData.put("currencySymbol", "₹");
                        userData.put("themeMode", "system");
                        userData.put("biometricEnabled", false);
                        userData.put("pinHash", "");
                        userData.put("onboardingComplete", false);
                        userData.put("createdAt", com.google.firebase.Timestamp.now());

                        mFirestore.collection("users").document(uid).set(userData)
                                .addOnSuccessListener(v -> DefaultDataSeeder.seedDefaultData(uid,
                                        a -> goTo(OnboardingActivity.class),
                                        e -> goTo(OnboardingActivity.class)))
                                .addOnFailureListener(e -> {
                                    showLoading(false);
                                    showError(e.getLocalizedMessage());
                                });
                    }
                })
                .addOnFailureListener(e -> goTo(MainActivity.class));
    }

    private void routeExisting(String pinHash, Boolean onboardingComplete) {
        showLoading(false);
        if (onboardingComplete != null && !onboardingComplete) {
            goTo(OnboardingActivity.class);
        } else if (pinHash != null && !pinHash.trim().isEmpty()) {
            Intent intent = new Intent(this, PinSetupActivity.class);
            intent.putExtra("mode", "VERIFY");
            startActivity(intent);
            finishAffinity();
        } else {
            goTo(MainActivity.class);
        }
    }

    private void goTo(Class<?> target) {
        showLoading(false);
        startActivity(new Intent(this, target));
        finishAffinity();
    }

    private void showLoading(boolean isLoading) {
        binding.loaderOverlay.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        if (message == null || message.isEmpty()) message = "Verification failed.";
        Snackbar snackbar = Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG);
        snackbar.setBackgroundTint(getResources().getColor(R.color.colorDanger));
        snackbar.show();
    }
}
