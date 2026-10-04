package com.example.moneylens;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.example.moneylens.databinding.ActivityPinSetupBinding;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.Executor;

public class PinSetupActivity extends AppCompatActivity {

    private ActivityPinSetupBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;
    private GoogleSignInClient mGoogleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    /** Mode: SETUP, VERIFY, CHANGE, FORGOT. */
    private String mode = "SETUP";
    private String correctPinHash = "";
    private String tempPin = "";
    private boolean isConfirming = false;
    private boolean isBiometricEnabled = false;

    private enum ChangeStep {
        VERIFY_CURRENT,
        ENTER_NEW,
        CONFIRM_NEW
    }
    private ChangeStep changeStep = ChangeStep.VERIFY_CURRENT;

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

        // Support both "pin_mode" and "mode" intent extras
        if (getIntent().hasExtra("pin_mode")) {
            mode = getIntent().getStringExtra("pin_mode");
        } else if (getIntent().hasExtra("mode")) {
            mode = getIntent().getStringExtra("mode");
        }
        if (mode != null) {
            mode = mode.toUpperCase(Locale.ROOT);
        } else {
            mode = "SETUP";
        }

        // Configure Google Sign-In client for re-authentication
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        handleGoogleReauthResult(task);
                    } else {
                        showLoading(false);
                    }
                }
        );

        setupUI();
        setupListeners();
        setupBackBehaviour();
        loadPinAndBiometricStatus();

        if ("FORGOT".equals(mode)) {
            binding.getRoot().post(this::initiateForgotPinFlow);
        }
    }

    /**
     * In VERIFY mode this activity IS the lock, so Back sends the task to the background.
     * In CHANGE or SETUP modes, Back steps back or finishes without trapping the user.
     */
    private void setupBackBehaviour() {
        getOnBackPressedDispatcher().addCallback(this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if ("VERIFY".equals(mode)) {
                            moveTaskToBack(true);
                        } else if ("CHANGE".equals(mode)) {
                            if (changeStep == ChangeStep.CONFIRM_NEW) {
                                changeStep = ChangeStep.ENTER_NEW;
                                tempPin = "";
                                clearInput();
                                binding.txtPinTitle.setText(getString(R.string.pin_change_new_title));
                                binding.txtPinSubtitle.setText(getString(R.string.pin_change_new_subtitle));
                                binding.btnUnlock.setText(getString(R.string.pin_continue));
                            } else {
                                finish();
                            }
                        } else if ("SETUP".equals(mode)) {
                            if (isConfirming) {
                                resetToCreateState();
                            } else {
                                finish();
                            }
                        } else {
                            finish();
                        }
                    }
                });
    }

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
            binding.btnUseFingerprint.setVisibility(isBiometricEnabled ? View.VISIBLE : View.GONE);
        } else if ("CHANGE".equals(mode)) {
            changeStep = ChangeStep.VERIFY_CURRENT;
            binding.txtPinTitle.setText(getString(R.string.pin_change_title));
            binding.txtPinSubtitle.setText(getString(R.string.pin_change_current_subtitle));
            binding.btnUnlock.setText(getString(R.string.pin_continue));
            binding.btnForgotPin.setVisibility(View.VISIBLE);
            binding.btnSwitchAccount.setVisibility(View.GONE);
            binding.btnUseFingerprint.setVisibility(View.GONE);
        } else if ("FORGOT".equals(mode)) {
            binding.txtPinTitle.setText(getString(R.string.pin_reauth_title));
            binding.txtPinSubtitle.setText(getString(R.string.pin_verify_subtitle));
            binding.btnUnlock.setText(getString(R.string.pin_continue));
            binding.btnForgotPin.setVisibility(View.VISIBLE);
            binding.btnSwitchAccount.setVisibility(View.VISIBLE);
            binding.btnUseFingerprint.setVisibility(View.GONE);
        } else {
            // SETUP Mode
            binding.txtPinTitle.setText(getString(R.string.pin_create_title));
            binding.txtPinSubtitle.setText(getString(R.string.pin_create_subtitle));
            binding.btnUnlock.setText(getString(R.string.pin_continue));
            binding.btnForgotPin.setVisibility(View.GONE);
            binding.btnSwitchAccount.setVisibility(View.GONE);
            binding.btnUseFingerprint.setVisibility(View.GONE);
        }
    }

    private void loadPinAndBiometricStatus() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();
        SharedPreferencesManager prefs = SharedPreferencesManager.getInstance(this);

        // Seed from the encrypted local mirror first, so the PIN can be entered and
        // checked with no network at all. Firestore then refreshes it below.
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
                });
    }

    private void setupListeners() {
        binding.btnUnlock.setOnClickListener(v -> submitPin());

        // Allow submitting from the keyboard's "Done" action
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

        // Forgot PIN — identity re-authentication flow
        binding.btnForgotPin.setOnClickListener(v -> initiateForgotPinFlow());

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
            handleSetupPin(pin);
        } else if ("CHANGE".equals(mode)) {
            handleChangePin(pin);
        } else {
            // VERIFY and FORGOT fallback
            handleVerifyPin(pin);
        }
    }

    private void handleSetupPin(String pin) {
        if (!isConfirming) {
            tempPin = pin;
            isConfirming = true;
            clearInput();
            binding.txtPinTitle.setText(getString(R.string.pin_confirm_title));
            binding.txtPinSubtitle.setText(getString(R.string.pin_confirm_subtitle));
            binding.btnUnlock.setText(getString(R.string.pin_confirm_action));
        } else {
            if (pin.equals(tempPin)) {
                savePinToFirestore(hashPinV2(pin));
            } else {
                shakeInput();
                showError(getString(R.string.pin_setup_mismatch));
                resetToCreateState();
            }
        }
    }

    private void handleChangePin(String pin) {
        switch (changeStep) {
            case VERIFY_CURRENT:
                if (correctPinHash == null || correctPinHash.trim().isEmpty()) {
                    shakeInput();
                    showError(getString(R.string.pin_unavailable_offline));
                    return;
                }
                if (verifyPin(pin, correctPinHash)) {
                    changeStep = ChangeStep.ENTER_NEW;
                    clearInput();
                    binding.txtPinTitle.setText(getString(R.string.pin_change_new_title));
                    binding.txtPinSubtitle.setText(getString(R.string.pin_change_new_subtitle));
                    binding.btnUnlock.setText(getString(R.string.pin_continue));
                    binding.btnForgotPin.setVisibility(View.GONE);
                } else {
                    shakeInput();
                    showError(getString(R.string.pin_change_incorrect_current));
                    clearInput();
                }
                break;

            case ENTER_NEW:
                tempPin = pin;
                changeStep = ChangeStep.CONFIRM_NEW;
                clearInput();
                binding.txtPinTitle.setText(getString(R.string.pin_change_confirm_title));
                binding.txtPinSubtitle.setText(getString(R.string.pin_change_confirm_subtitle));
                binding.btnUnlock.setText(getString(R.string.pin_confirm_action));
                break;

            case CONFIRM_NEW:
                if (pin.equals(tempPin)) {
                    saveNewPinFromChange(hashPinV2(pin));
                } else {
                    shakeInput();
                    showError(getString(R.string.pin_setup_mismatch));
                    changeStep = ChangeStep.ENTER_NEW;
                    tempPin = "";
                    clearInput();
                    binding.txtPinTitle.setText(getString(R.string.pin_change_new_title));
                    binding.txtPinSubtitle.setText(getString(R.string.pin_change_new_subtitle));
                    binding.btnUnlock.setText(getString(R.string.pin_continue));
                }
                break;
        }
    }

    private void handleVerifyPin(String pin) {
        if (correctPinHash == null || correctPinHash.trim().isEmpty()) {
            shakeInput();
            showError(getString(R.string.pin_unavailable_offline));
            return;
        }
        if (verifyPin(pin, correctPinHash)) {
            // Transparently upgrade legacy unsalted hashes to the salted v2$ format
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

    /**
     * Recovery flow: verifies user identity via Google, Password, or Phone before clearing PIN.
     */
    private void initiateForgotPinFlow() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            goToLogin();
            return;
        }

        boolean hasGoogle = false;
        boolean hasPassword = false;
        boolean hasPhone = false;

        for (UserInfo profile : user.getProviderData()) {
            String providerId = profile.getProviderId();
            if (GoogleAuthProvider.PROVIDER_ID.equals(providerId)) {
                hasGoogle = true;
            } else if (EmailAuthProvider.PROVIDER_ID.equals(providerId)) {
                hasPassword = true;
            } else if (PhoneAuthProvider.PROVIDER_ID.equals(providerId)) {
                hasPhone = true;
            }
        }

        if (hasGoogle) {
            promptGoogleReauth(user);
        } else if (hasPassword) {
            promptPasswordReauth(user);
        } else if (hasPhone) {
            promptPhoneReauth();
        } else {
            promptPasswordReauth(user);
        }
    }

    private void promptGoogleReauth(FirebaseUser user) {
        String email = user.getEmail() != null ? user.getEmail() : "Google";
        new AlertDialog.Builder(this)
                .setTitle(R.string.pin_reauth_title)
                .setMessage(getString(R.string.pin_reauth_google_msg, email))
                .setPositiveButton(R.string.pin_reauth_btn_google, (d, w) -> {
                    showLoading(true);
                    mGoogleSignInClient.signOut().addOnCompleteListener(t ->
                            googleSignInLauncher.launch(mGoogleSignInClient.getSignInIntent()));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void handleGoogleReauthResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            if (account != null && account.getIdToken() != null) {
                AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
                FirebaseUser user = mAuth.getCurrentUser();
                if (user != null) {
                    showLoading(true);
                    user.reauthenticate(credential)
                            .addOnSuccessListener(authResult -> onReauthSuccess())
                            .addOnFailureListener(e -> {
                                showLoading(false);
                                showError(getString(R.string.pin_reauth_failed) + ": " + e.getMessage());
                            });
                }
            } else {
                showLoading(false);
                showError(getString(R.string.pin_reauth_failed));
            }
        } catch (ApiException e) {
            showLoading(false);
            showError("Google verification failed: " + e.getMessage());
        }
    }

    private void promptPasswordReauth(FirebaseUser user) {
        String email = user.getEmail() != null ? user.getEmail() : "";
        TextInputLayout passwordLayout = new TextInputLayout(this);
        passwordLayout.setHint(getString(R.string.pin_reauth_password_hint));
        passwordLayout.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        passwordLayout.setPadding(pad, pad / 2, pad, 0);

        TextInputEditText passwordInput = new TextInputEditText(passwordLayout.getContext());
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordLayout.addView(passwordInput);

        new AlertDialog.Builder(this)
                .setTitle(R.string.pin_reauth_title)
                .setMessage(getString(R.string.pin_reauth_email_msg, email))
                .setView(passwordLayout)
                .setPositiveButton(R.string.pin_reauth_btn_verify, (d, which) -> {
                    String password = passwordInput.getText() == null ? "" : passwordInput.getText().toString().trim();
                    if (password.isEmpty()) {
                        showError(getString(R.string.err_empty_password));
                        return;
                    }
                    AuthCredential credential = EmailAuthProvider.getCredential(email, password);
                    showLoading(true);
                    user.reauthenticate(credential)
                            .addOnSuccessListener(authResult -> onReauthSuccess())
                            .addOnFailureListener(e -> {
                                showLoading(false);
                                showError(getString(R.string.pin_reauth_failed) + ": " + e.getLocalizedMessage());
                            });
                })
                .setNeutralButton(R.string.forgot_password, (d, which) -> {
                    if (!email.isEmpty()) {
                        showLoading(true);
                        mAuth.sendPasswordResetEmail(email)
                                .addOnSuccessListener(v -> {
                                    showLoading(false);
                                    Toast.makeText(PinSetupActivity.this,
                                            getString(R.string.pin_forgot_password_reset_sent, email),
                                            Toast.LENGTH_LONG).show();
                                })
                                .addOnFailureListener(e -> {
                                    showLoading(false);
                                    showError("Failed to send reset email: " + e.getMessage());
                                });
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void promptPhoneReauth() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.pin_forgot_dialog_title)
                .setMessage(R.string.pin_reset_phone_msg)
                .setPositiveButton(R.string.pin_forgot_confirm, (d, w) -> resetPinAndReLogin())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void onReauthSuccess() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();
        showLoading(true);
        mFirestore.collection("users").document(uid)
                .set(Collections.singletonMap("pinHash", ""), SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    SharedPreferencesManager prefs = SharedPreferencesManager.getInstance(this);
                    prefs.clearCachedPin();
                    correctPinHash = "";
                    Snackbar.make(binding.getRoot(), R.string.pin_reset_reauth_success, Snackbar.LENGTH_LONG).show();
                    // Transition to SETUP mode directly so the user can set a new PIN
                    mode = "SETUP";
                    resetToCreateState();
                    setupUI();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    showError("Failed to reset PIN: " + e.getMessage());
                });
    }

    /**
     * Clears stored PIN and sends phone users through OTP re-verification.
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

    private void goToLogin() {
        Intent intent = new Intent(PinSetupActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /** Sign out of the current account and return to login screen to switch users. */
    private void switchAccount() {
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
        SharedPreferencesManager prefs = SharedPreferencesManager.getInstance(this);
        prefs.clearCachedPin();
        prefs.setAppLockEnabled(false);
        unlocked = true;
        BaseActivity.setAppUnlocked();
        mAuth.signOut();
        goToLogin();
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
                    prefs.setAppLockEnabled(true);
                    unlocked = true;
                    BaseActivity.setAppUnlocked();
                    startActivity(new Intent(PinSetupActivity.this, BiometricSetupActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    showError("Failed to configure PIN lock: " + e.getMessage());
                    resetToCreateState();
                });
    }

    private void saveNewPinFromChange(String hash) {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        showLoading(true);
        mFirestore.collection("users").document(uid)
                .set(Collections.singletonMap("pinHash", hash), SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    SharedPreferencesManager prefs = SharedPreferencesManager.getInstance(this);
                    prefs.setCachedPin(uid, hash);
                    prefs.setAppLockEnabled(true);
                    Toast.makeText(this, R.string.pin_change_success, Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    showError("Failed to update PIN: " + e.getMessage());
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
