package com.example.bachatkhata;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.example.bachatkhata.databinding.ActivityPinSetupBinding;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
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
    private boolean isConfirming = false;
    private boolean isBiometricEnabled = false;
    /** Set once the user gets past this screen, so onDestroy knows not to re-arm the lock. */
    private boolean unlocked = false;

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
        setupBackBehaviour();
        loadPinAndBiometricStatus();
    }

    /**
     * In VERIFY mode this activity IS the lock, so Back must not dismiss it. It sends
     * the task to the background instead — the same thing the system lock screen does —
     * leaving this screen as the resume point. Previously Back simply finished the
     * activity, which dropped the user onto whatever was behind it.
     */
    private void setupBackBehaviour() {
        if (!"VERIFY".equals(mode)) return;
        getOnBackPressedDispatcher().addCallback(this,
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        moveTaskToBack(true);
                    }
                });
    }

    /**
     * If this screen goes away while the app is still locked, hand the launch claim
     * back so the lock can be shown again. Without this the claim stayed set for the
     * life of the process and every later check short-circuited, leaving the app
     * permanently unlocked.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if ("VERIFY".equals(mode) && !unlocked) {
            BaseActivity.releaseLockScreen();
        }
    }

    /** Everything that must happen exactly once, when the user is let through. */
    private void enterApp() {
        unlocked = true;
        BaseActivity.setAppUnlocked();
        startActivity(new Intent(PinSetupActivity.this, MainActivity.class)
                .putExtra(MainActivity.EXTRA_SKIP_BIOMETRIC, true));
        finish();
    }

    private void setupUI() {
        if ("VERIFY".equals(mode)) {
            binding.txtPinTitle.setText(getString(R.string.pin_verify_title));
            binding.txtPinSubtitle.setText(getString(R.string.pin_verify_subtitle));
            binding.btnUnlock.setText(getString(R.string.pin_unlock));
            binding.btnForgotPin.setVisibility(View.VISIBLE);
            binding.btnSwitchAccount.setVisibility(View.VISIBLE);
        } else {
            binding.txtPinTitle.setText(getString(R.string.pin_create_title));
            binding.txtPinSubtitle.setText(getString(R.string.pin_create_subtitle));
            binding.btnUnlock.setText(getString(R.string.pin_continue));
            binding.btnForgotPin.setVisibility(View.GONE);
            binding.btnSwitchAccount.setVisibility(View.GONE);
        }
    }

    private void loadPinAndBiometricStatus() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();
        SharedPreferencesManager prefs = SharedPreferencesManager.getInstance(this);

        // Seed from the encrypted local mirror first, so the PIN can be entered and
        // checked with no network at all. Firestore then refreshes it below to pick up
        // a PIN changed on another device.
        correctPinHash = prefs.getCachedPinHash(uid);

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
                        prefs.setCachedPin(uid, pinHash);
                        if (bioEnabled != null) {
                            isBiometricEnabled = bioEnabled;
                        }

                        // Auto-prompt fingerprint if verify mode & biometric enabled
                        if ("VERIFY".equals(mode) && isBiometricEnabled) {
                            binding.btnUseFingerprint.setVisibility(View.VISIBLE);
                            promptBiometrics();
                        } else {
                            binding.btnUseFingerprint.setVisibility(View.GONE);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    // Offline. The cached hash above still verifies the PIN; only the
                    // case where nothing was ever cached is unverifiable, and that is
                    // said plainly at submit time rather than rejecting every attempt
                    // as though the PIN were wrong.
                });
    }

    private void setupListeners() {
        binding.btnUnlock.setOnClickListener(v -> submitPin());

        // Allow submitting from the keyboard's "Done" action.
        binding.etPin.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitPin();
                return true;
            }
            return false;
        });

        // Fingerprint
        binding.btnUseFingerprint.setOnClickListener(v -> {
            if (isBiometricEnabled) {
                promptBiometrics();
            } else {
                Snackbar.make(binding.getRoot(), "Biometrics not enabled on profile.", Snackbar.LENGTH_SHORT).show();
            }
        });

        // Forgot PIN — reset via phone re-verification
        binding.btnForgotPin.setOnClickListener(v -> showForgotPinDialog());

        // Switch account — sign out and return to the login screen
        binding.btnSwitchAccount.setOnClickListener(v -> switchAccount());
    }

    private void submitPin() {
        String pin = binding.etPin.getText() == null ? "" : binding.etPin.getText().toString().trim();
        if (pin.length() < 4) {
            showError(getString(R.string.pin_too_short));
            shakeInput();
            return;
        }

        if ("SETUP".equals(mode)) {
            if (!isConfirming) {
                // First step of PIN setup
                tempPin = pin;
                isConfirming = true;
                clearInput();
                binding.txtPinTitle.setText(getString(R.string.pin_confirm_title));
                binding.txtPinSubtitle.setText(getString(R.string.pin_confirm_subtitle));
                binding.btnUnlock.setText(getString(R.string.pin_confirm_action));
            } else {
                // Confirmation step of PIN setup
                if (pin.equals(tempPin)) {
                    savePinToFirestore(hashPinV2(pin));
                } else {
                    shakeInput();
                    showError(getString(R.string.pin_setup_mismatch));
                    resetToCreateState();
                }
            }
        } else {
            // VERIFY Mode
            if (correctPinHash == null || correctPinHash.trim().isEmpty()) {
                // Nothing to check against: the profile has never synced to this device
                // and there is no cached hash. Rejecting the PIN would read as "wrong
                // PIN" and send the user round in circles, so name the real problem.
                shakeInput();
                showError(getString(R.string.pin_unavailable_offline));
                return;
            }
            if (verifyPin(pin, correctPinHash)) {
                // Transparently upgrade legacy unsalted hashes to the salted v2$ format.
                if (!correctPinHash.startsWith("v2$")) {
                    savePinHashSilently(hashPinV2(pin));
                }
                enterApp();
            } else {
                shakeInput();
                showError(getString(R.string.pin_incorrect));
                clearInput();
            }
        }
    }

    private void resetToCreateState() {
        tempPin = "";
        isConfirming = false;
        clearInput();
        binding.txtPinTitle.setText(getString(R.string.pin_create_title));
        binding.txtPinSubtitle.setText(getString(R.string.pin_create_subtitle));
        binding.btnUnlock.setText(getString(R.string.pin_continue));
    }

    private void clearInput() {
        binding.etPin.setText("");
    }

    private void showForgotPinDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.pin_forgot_dialog_title)
                .setMessage(R.string.pin_forgot_dialog_message)
                .setPositiveButton(R.string.pin_forgot_confirm, (d, w) -> resetPinAndReLogin())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Clears the stored PIN and sends the user back through phone verification. The PIN can only
     * be re-created after the account owner proves ownership via OTP, so a forgotten PIN never
     * lets an unauthorised person bypass the lock.
     */
    private void resetPinAndReLogin() {
        if (mAuth.getCurrentUser() == null) {
            goToPhoneLogin();
            return;
        }
        String uid = mAuth.getCurrentUser().getUid();
        showLoading(true);
        mFirestore.collection("users").document(uid)
                .set(Collections.singletonMap("pinHash", ""), SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    // Drop the local mirror too. Leaving it behind would keep enforcing
                    // the PIN that was just cleared, since the lock now trusts the cache.
                    // The idle lock goes with it — there is no PIN left to satisfy it.
                    SharedPreferencesManager prefs = SharedPreferencesManager.getInstance(this);
                    prefs.clearCachedPin();
                    prefs.setAppLockEnabled(false);
                    unlocked = true;
                    BaseActivity.setAppUnlocked();
                    mAuth.signOut();
                    goToPhoneLogin();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    showError("Couldn't reset PIN: " + e.getMessage());
                });
    }

    private void goToPhoneLogin() {
        Intent intent = new Intent(PinSetupActivity.this, PhoneLoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /** Sign out of the current account and return to the login screen to switch users. */
    private void switchAccount() {
        // Same durability guard as ProfileFragment's logout: a user who edited
        // offline, backgrounded the app, and hit Switch Account on the lock screen
        // would otherwise lose those writes on sign-out. Flush first, and warn if
        // the flush can't complete (e.g. still offline) rather than discarding
        // silently. The wait never resolves offline, so it needs its own timeout.
        showLoading(true);
        final boolean[] settled = {false};
        final android.os.Handler handler = new android.os.Handler(getMainLooper());

        Runnable onTimeout = () -> {
            if (settled[0]) return;
            settled[0] = true;
            showLoading(false);
            confirmSwitchWithUnsyncedWrites();
        };
        handler.postDelayed(onTimeout, 5000);

        FirebaseFirestore.getInstance().waitForPendingWrites()
                .addOnCompleteListener(task -> {
                    if (settled[0]) return;
                    settled[0] = true;
                    handler.removeCallbacks(onTimeout);
                    showLoading(false);
                    if (task.isSuccessful()) {
                        performSwitchAccount();
                    } else {
                        confirmSwitchWithUnsyncedWrites();
                    }
                });
    }

    private void confirmSwitchWithUnsyncedWrites() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.logout_unsynced_title)
                .setMessage(R.string.logout_unsynced_message)
                .setPositiveButton(R.string.logout_unsynced_confirm, (d, which) -> performSwitchAccount())
                .setNegativeButton(R.string.logout_unsynced_cancel, null)
                .show();
    }

    private void performSwitchAccount() {
        // The next account gets its PIN state from Firestore, not from this one's, and
        // must not inherit this one's idle-lock setting either.
        SharedPreferencesManager prefs = SharedPreferencesManager.getInstance(this);
        prefs.clearCachedPin();
        prefs.setAppLockEnabled(false);
        unlocked = true;
        BaseActivity.setAppUnlocked();
        mAuth.signOut();
        Intent intent = new Intent(PinSetupActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void savePinToFirestore(String hash) {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        showLoading(true);
        mFirestore.collection("users").document(uid)
                .set(Collections.singletonMap("pinHash", hash), SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    SharedPreferencesManager prefs = SharedPreferencesManager.getInstance(this);
                    prefs.setCachedPin(uid, hash);
                    // Arm the idle lock. Setting a PIN is the act of asking for one, and
                    // nothing else in the app ever turned this flag on — so the lock
                    // screen only ever appeared on a cold start, never on returning from
                    // the background, however the PIN was configured.
                    prefs.setAppLockEnabled(true);
                    // Successfully saved, go to BiometricSetupActivity
                    startActivity(new Intent(PinSetupActivity.this, BiometricSetupActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    showError("Failed to configure PIN lock: " + e.getMessage());
                    resetToCreateState();
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
                            enterApp();
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

    private void shakeInput() {
        TranslateAnimation shake = new TranslateAnimation(0, 10, 0, 0);
        shake.setDuration(50);
        shake.setRepeatMode(Animation.REVERSE);
        shake.setRepeatCount(5);
        binding.tilPin.startAnimation(shake);
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
        SharedPreferencesManager.getInstance(this).setCachedPin(uid, hash);
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
