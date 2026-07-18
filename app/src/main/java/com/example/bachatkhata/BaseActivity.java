package com.example.bachatkhata;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ProgressBar;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class BaseActivity extends AppCompatActivity {

    private static int activeActivitiesCount = 0;
    private static long backgroundTimeMs = -1;
    private static boolean isAppLocked = false;
    // Guards against launching the lock screen more than once. checkPinConfigAndLock()
    // runs from both onStart() and onResume() and does an async Firestore read, so
    // without this flag two (or more) PinSetupActivity instances could be launched,
    // each auto-prompting biometrics. Set synchronously before the async read, and
    // cleared once the lock is resolved (via setAppUnlocked / no-PIN / failure).
    private static boolean isLockScreenActive = false;
    private AlertDialog loadingDialog;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    // --- Edge-to-edge inset handling -------------------------------------
    // Android 15 (targetSdk 35) draws content behind the status/navigation
    // bars by default. Instead of guessing the bar height with hardcoded
    // margins, we pad the content root by the REAL system-bar insets so the
    // UI looks correct on every device (Samsung punch-holes, gesture nav,
    // notches, tablets, landscape). Every BaseActivity subclass gets this
    // automatically via the setContentView overrides below.

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        applyEdgeToEdgeInsets(findViewById(android.R.id.content));
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        applyEdgeToEdgeInsets(findViewById(android.R.id.content));
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        super.setContentView(view, params);
        applyEdgeToEdgeInsets(findViewById(android.R.id.content));
    }

    /**
     * Pads {@code root} by the current system-bar insets (added on top of any
     * padding the view already has). Safe to call from any Activity — used by
     * the standalone AppCompatActivity screens that don't extend BaseActivity.
     */
    public static void applyEdgeToEdgeInsets(final View root) {
        if (root == null) return;
        final int basePaddingLeft = root.getPaddingLeft();
        final int basePaddingTop = root.getPaddingTop();
        final int basePaddingRight = root.getPaddingRight();
        final int basePaddingBottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    basePaddingLeft + bars.left,
                    basePaddingTop + bars.top,
                    basePaddingRight + bars.right,
                    basePaddingBottom + bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Check background timeout on start
        if (activeActivitiesCount == 0 && backgroundTimeMs != -1) {
            long idleTime = System.currentTimeMillis() - backgroundTimeMs;
            SharedPreferencesManager sp = SharedPreferencesManager.getInstance(this);
            int timeoutMs = sp.getLockTimeoutSeconds() * 1000;
            if (sp.isAppLockEnabled() && idleTime > timeoutMs) {
                isAppLocked = true;
            }
        }

        activeActivitiesCount++;
        backgroundTimeMs = -1; // Reset

        if (isAppLocked && shouldEnforceLock()) {
            checkPinConfigAndLock();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Double check lock state on resume if required
        if (isAppLocked && shouldEnforceLock()) {
            checkPinConfigAndLock();
        }
    }

    // --- Sticky Immersive Mode --------------------------------------------
    // Hides the 3 system navigation buttons (back, home, recent) when the app
    // is active. The user can swipe-up from the bottom edge to reveal them
    // temporarily; they auto-hide again after a short delay.

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && shouldApplyImmersiveMode()) {
            enableStickyImmersiveMode();
        }
    }

    /**
     * Enables sticky immersive mode: hides both status bar and navigation bar.
     * When the user swipes from the edge, the bars appear translucently and
     * auto-hide after a moment.
     */
    private void enableStickyImmersiveMode() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            // Bars appear translucently on swipe and auto-hide
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            // Hide the system navigation bar (the 3 buttons)
            controller.hide(WindowInsetsCompat.Type.navigationBars());
        }
    }

    /**
     * Returns true for screens that should run in immersive mode.
     * Auth/lock/onboarding screens are excluded so the user can always
     * see the system navigation while logging in or setting up.
     */
    private boolean shouldApplyImmersiveMode() {
        String className = getClass().getSimpleName();
        return !className.equals("PinSetupActivity")
                && !className.equals("SplashActivity")
                && !className.equals("LoginActivity")
                && !className.equals("RegisterActivity")
                && !className.equals("ForgotPasswordActivity")
                && !className.equals("OnboardingActivity")
                && !className.equals("BiometricSetupActivity")
                && !className.equals("PhoneLoginActivity");
    }

    @Override
    protected void onStop() {
        super.onStop();
        activeActivitiesCount--;

        if (activeActivitiesCount == 0) {
            backgroundTimeMs = System.currentTimeMillis();
        }
    }

    private boolean shouldEnforceLock() {
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
        // Already showing (or in the middle of launching) the lock screen — don't
        // fire a second launch. This call comes from both onStart() and onResume().
        if (isLockScreenActive) {
            return;
        }

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            isAppLocked = false;
            return;
        }

        // Claim the lock synchronously, before the async read, so a near-simultaneous
        // onResume() call bails out at the guard above instead of racing us.
        isLockScreenActive = true;

        String uid = auth.getCurrentUser().getUid();
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String pinHash = documentSnapshot.getString("pinHash");
                        if (pinHash != null && !pinHash.trim().isEmpty()) {
                            // Keep isLockScreenActive == true until the user unlocks
                            // (setAppUnlocked) so the lock screen isn't relaunched.
                            Intent intent = new Intent(BaseActivity.this, PinSetupActivity.class);
                            intent.putExtra("mode", "VERIFY");
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        } else {
                            isAppLocked = false;
                            isLockScreenActive = false;
                        }
                    } else {
                        isAppLocked = false;
                        isLockScreenActive = false;
                    }
                })
                .addOnFailureListener(e -> {
                    isAppLocked = false;
                    isLockScreenActive = false;
                });
    }

    public static void setAppUnlocked() {
        isAppLocked = false;
        isLockScreenActive = false;
        backgroundTimeMs = -1;
    }

    // Utility: Show Clay loading ProgressBar Dialog
    public void showLoadingDialog() {
        if (isFinishing() || isDestroyed()) return;

        if (loadingDialog == null) {
            ProgressBar progressBar = new ProgressBar(this);
            progressBar.setIndeterminate(true);
            
            loadingDialog = new AlertDialog.Builder(this)
                    .setView(progressBar)
                    .setCancelable(false)
                    .create();
            
            if (loadingDialog.getWindow() != null) {
                loadingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }
        }
        if (!loadingDialog.isShowing()) {
            loadingDialog.show();
        }
    }

    public void hideLoadingDialog() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }

    // Utility: Show Clay styled Snackbar
    public void showSnackbar(String message, String type) {
        View rootView = findViewById(android.R.id.content);
        if (rootView == null) return;

        Snackbar snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_LONG);
        int color = Color.parseColor("#7C6FE0"); // Default INFO colorPrimary (soft purple)

        if ("SUCCESS".equalsIgnoreCase(type)) {
            color = Color.parseColor("#5DCAA5"); // soft green (colorSecondary)
        } else if ("ERROR".equalsIgnoreCase(type)) {
            color = Color.parseColor("#E24B4A"); // soft red (colorDanger)
        } else if ("INFO".equalsIgnoreCase(type)) {
            color = Color.parseColor("#7C6FE0"); // soft purple (colorPrimary)
        }

        snackbar.setBackgroundTint(color);
        snackbar.setTextColor(Color.WHITE);
        snackbar.show();
    }

    // Utility: Hide keyboard
    public void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }
}
