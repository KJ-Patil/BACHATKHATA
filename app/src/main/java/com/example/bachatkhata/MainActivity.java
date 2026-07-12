package com.example.bachatkhata;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Outline;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

import java.util.Calendar;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

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

        // Disable the uniform icon tint so each menu icon shows its own colors
        // (e.g. the red ledger/book icon and the state-colored home/list/budget icons).
        binding.bottomNavigationView.setItemIconTintList(null);

        // 2. Setup FAB Click and Entry Animation
        binding.fabAdd.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, AddTransactionActivity.class));
        });
        animateFABEntry();
        applyRoundedTopBar();

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

        // 5. Schedule Background Periodic Tasks (WorkManager)
        scheduleAllWorkers();

        // 6. Handle deep links from intent (if launched from notification)
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null || navController == null) return;

        if (intent.hasExtra("parsed_transaction")) {
            SmsParser.ParsedTransaction txn = (SmsParser.ParsedTransaction) intent.getSerializableExtra("parsed_transaction");
            if (txn != null) {
                SmsImportBottomSheet bottomSheet = SmsImportBottomSheet.newInstance(txn);
                bottomSheet.show(getSupportFragmentManager(), "SmsImportBottomSheet");
            }
            intent.removeExtra("parsed_transaction");
        }

        String destination = intent.getStringExtra("destination");
        if (destination != null) {
            switch (destination) {
                case "budget":
                    navController.navigate(R.id.navigation_budget);
                    break;
                case "notifications":
                case "alert":
                case "bill":
                    navController.navigate(R.id.navigation_notifications);
                    break;
                case "transactions":
                    navController.navigate(R.id.navigation_transactions);
                    break;
                case "savings":
                    navController.navigate(R.id.navigation_savings);
                    break;
                case "health":
                    navController.navigate(R.id.navigation_health_score);
                    break;
                case "mood":
                    Intent moodIntent = new Intent(this, MoodInsightActivity.class);
                    startActivity(moodIntent);
                    break;
                default:
                    // Navigate to home by default
                    navController.navigate(R.id.navigation_home);
                    break;
            }
        }
    }

    private void scheduleAllWorkers() {
        androidx.work.WorkManager workManager = androidx.work.WorkManager.getInstance(this);

        // 1. BudgetAlertWorker - Daily 9 AM
        long budgetDelay = calculateDailyDelay(9);
        androidx.work.PeriodicWorkRequest budgetCheckRequest =
                new androidx.work.PeriodicWorkRequest.Builder(BudgetAlertWorker.class, 24, TimeUnit.HOURS)
                        .setInitialDelay(budgetDelay, TimeUnit.MILLISECONDS)
                        .build();
        workManager.enqueueUniquePeriodicWork(
                "BudgetAlertCheck",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                budgetCheckRequest
        );

        // 2. WeeklyInsightWorker - Monday 8 AM
        long weeklyDelay = calculateWeeklyDelay(Calendar.MONDAY, 8);
        androidx.work.PeriodicWorkRequest weeklyInsightRequest =
                new androidx.work.PeriodicWorkRequest.Builder(WeeklyInsightWorker.class, 7, TimeUnit.DAYS)
                        .setInitialDelay(weeklyDelay, TimeUnit.MILLISECONDS)
                        .build();
        workManager.enqueueUniquePeriodicWork(
                "WeeklyInsightCheck",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                weeklyInsightRequest
        );

        // 3. BillReminderWorker - Daily 8 AM
        long billDelay = calculateDailyDelay(8);
        androidx.work.PeriodicWorkRequest billReminderRequest =
                new androidx.work.PeriodicWorkRequest.Builder(BillReminderWorker.class, 24, TimeUnit.HOURS)
                        .setInitialDelay(billDelay, TimeUnit.MILLISECONDS)
                        .build();
        workManager.enqueueUniquePeriodicWork(
                "BillReminderCheck",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                billReminderRequest
        );

        // 4. HealthScoreWorker - Weekly Sunday 9 AM
        long healthDelay = calculateWeeklyDelay(Calendar.SUNDAY, 9);
        androidx.work.PeriodicWorkRequest healthCheckRequest =
                new androidx.work.PeriodicWorkRequest.Builder(HealthScoreWorker.class, 7, TimeUnit.DAYS)
                        .setInitialDelay(healthDelay, TimeUnit.MILLISECONDS)
                        .build();
        workManager.enqueueUniquePeriodicWork(
                "HealthScoreCheck",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                healthCheckRequest
        );

        // 5. MoodCheckInWorker - Weekly Sunday 7 PM
        long moodDelay = calculateWeeklyDelay(Calendar.SUNDAY, 19);
        androidx.work.PeriodicWorkRequest moodCheckRequest =
                new androidx.work.PeriodicWorkRequest.Builder(MoodCheckInWorker.class, 7, TimeUnit.DAYS)
                        .setInitialDelay(moodDelay, TimeUnit.MILLISECONDS)
                        .build();
        workManager.enqueueUniquePeriodicWork(
                "MoodCheckInCheck",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                moodCheckRequest
        );
    }

    private long calculateDailyDelay(int targetHour) {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, targetHour);
        target.set(Calendar.MINUTE, 0);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);
        if (target.before(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1);
        }
        return target.getTimeInMillis() - now.getTimeInMillis();
    }

    private long calculateWeeklyDelay(int targetDayOfWeek, int targetHour) {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.DAY_OF_WEEK, targetDayOfWeek);
        target.set(Calendar.HOUR_OF_DAY, targetHour);
        target.set(Calendar.MINUTE, 0);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);
        if (target.before(now)) {
            target.add(Calendar.WEEK_OF_YEAR, 1);
        }
        return target.getTimeInMillis() - now.getTimeInMillis();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isBiometricEnabled && !isBiometricPromptShown) {
            promptBiometrics();
        }
    }

    /** Rounds the top-left and top-right corners of the bottom bar for a soft, floating look. */
    private void applyRoundedTopBar() {
        final float radius = getResources().getDisplayMetrics().density * 22f;
        binding.bottomAppBar.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                // Extend the rect below the view so only the top corners are rounded.
                outline.setRoundRect(0, 0, view.getWidth(),
                        view.getHeight() + (int) radius, radius);
            }
        });
        binding.bottomAppBar.setClipToOutline(true);
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
                            isBiometricPromptShown = false;
                            Intent intent = new Intent(MainActivity.this, PinSetupActivity.class);
                            intent.putExtra("mode", "VERIFY");
                            startActivity(intent);
                        }

                        @Override
                        public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                            super.onAuthenticationSucceeded(result);
                            isBiometricPromptShown = false;
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