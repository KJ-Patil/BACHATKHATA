package com.example.bachatkhata;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.gms.tasks.OnFailureListener;
import com.example.bachatkhata.databinding.ActivityAddTransactionBinding;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.firestore.DocumentReference;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AddTransactionActivity extends BaseActivity {

    private ActivityAddTransactionBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;

    private String transactionType = "expense"; // "income" or "expense"
    private Category selectedCategory = null;
    private Date selectedDate = new Date();
    private String selectedAccount = "Cash";
    private String userCurrencyCode = "INR";
    private String userCurrencySymbol = "₹";

    private CategoryGridAdapter categoryGridAdapter;
    private final List<Category> availableCategories = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.US);

    private String groupId = null;
    private String groupName = null;

    private ActivityResultLauncher<String> requestRecordAudioLauncher;
    private VoiceLoggingHelper voiceLoggingHelper;
    private ActivityResultLauncher<Intent> scanReceiptLauncher;

    // Timeout handler to prevent infinite loading when Firestore server is unreachable
    private final android.os.Handler timeoutHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable saveTimeoutRunnable;
    private boolean saveCompleted = false;
    private static final long SAVE_TIMEOUT_MS = 10000; // 10 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddTransactionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();

        voiceLoggingHelper = new VoiceLoggingHelper();
        requestRecordAudioLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        startVoiceLogging();
                    } else {
                        showError("Audio recording permission is required for voice logging.");
                    }
                }
        );

        scanReceiptLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        double amount = data.getDoubleExtra("amount", 0.0);
                        String merchant = data.getStringExtra("merchant");
                        long dateMs = data.getLongExtra("date", 0);
                        
                        if (amount > 0) {
                            binding.etAmount.setText(String.format(Locale.US, "%.2f", amount));
                        }
                        if (merchant != null && !merchant.trim().isEmpty()) {
                            binding.etNote.setText(merchant);
                        }
                        if (dateMs > 0) {
                            selectedDate = new Date(dateMs);
                            binding.txtSelectedDate.setText(dateFormat.format(selectedDate));
                        }
                        
                        String mappedCategory = SmsParser.mapMerchantToCategory(merchant);
                        categoryGridAdapter.setSelectedCategoryByName(mappedCategory);
                        selectedCategory = categoryGridAdapter.getSelectedCategory();
                        
                        showSuccess("Receipt details loaded!");
                    }
                }
        );

        groupId = getIntent().getStringExtra("groupId");
        groupName = getIntent().getStringExtra("groupName");

        userCurrencyCode = CurrencyManager.getInstance().getCurrentCurrencyCode();
        userCurrencySymbol = CurrencyManager.getInstance().getCurrentCurrencySymbol();
        binding.txtCurrencySymbolLarge.setText(userCurrencySymbol);

        setupToggleGroup();
        setupCategoryRecyclerView();
        setupDatePicker();
        setupAccountPicker();
        setupListeners();

        loadCategories();
        loadRunningBalance();

        if (getIntent().getBooleanExtra("startVoiceLogging", false)) {
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::checkVoicePermissionAndStart, 500);
        }
    }

    private void setupToggleGroup() {
        // Default color for Expense
        binding.btnToggleExpense.setBackgroundColor(Color.parseColor("#E24B4A")); // colorDanger
        binding.btnToggleExpense.setTextColor(Color.WHITE);
        binding.btnToggleIncome.setBackgroundColor(Color.TRANSPARENT);
        binding.btnToggleIncome.setTextColor(Color.parseColor("#7C6FE0"));

        binding.toggleTypeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnToggleIncome) {
                transactionType = "income";
                binding.btnToggleIncome.setBackgroundColor(Color.parseColor("#5DCAA5")); // colorSecondary
                binding.btnToggleIncome.setTextColor(Color.WHITE);
                binding.btnToggleExpense.setBackgroundColor(Color.TRANSPARENT);
                binding.btnToggleExpense.setTextColor(Color.parseColor("#7C6FE0"));
                binding.etAmount.setTextColor(Color.parseColor("#3DAF85"));
                binding.txtCurrencySymbolLarge.setTextColor(Color.parseColor("#3DAF85"));
            } else {
                transactionType = "expense";
                binding.btnToggleExpense.setBackgroundColor(Color.parseColor("#E24B4A")); // colorDanger
                binding.btnToggleExpense.setTextColor(Color.WHITE);
                binding.btnToggleIncome.setBackgroundColor(Color.TRANSPARENT);
                binding.btnToggleIncome.setTextColor(Color.parseColor("#7C6FE0"));
                binding.etAmount.setTextColor(Color.parseColor("#E24B4A"));
                binding.txtCurrencySymbolLarge.setTextColor(Color.parseColor("#E24B4A"));
            }
            loadCategories();
        });
    }

    private void setupCategoryRecyclerView() {
        categoryGridAdapter = new CategoryGridAdapter(category -> {
            selectedCategory = category;
        });
        binding.rvCategoryGrid.setAdapter(categoryGridAdapter);
    }

    private void setupDatePicker() {
        binding.txtSelectedDate.setText(dateFormat.format(selectedDate));
        binding.cardDatePicker.setOnClickListener(v -> {
            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Select Transaction Date")
                    .setSelection(selectedDate.getTime())
                    .build();

            datePicker.addOnPositiveButtonClickListener(selection -> {
                selectedDate = new Date(selection);
                binding.txtSelectedDate.setText(dateFormat.format(selectedDate));
            });

            datePicker.show(getSupportFragmentManager(), "DATE_PICKER");
        });
    }

    private void setupAccountPicker() {
        binding.txtSelectedAccount.setText(selectedAccount);
        binding.cardAccountSelector.setOnClickListener(v -> {
            AccountPickerBottomSheet.newInstance(accountName -> {
                selectedAccount = accountName;
                binding.txtSelectedAccount.setText(accountName);
            }).show(getSupportFragmentManager(), "ACCOUNT_PICKER");
        });
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());
        
        binding.btnMic.setOnClickListener(v -> checkVoicePermissionAndStart());

        binding.btnScanReceipt.setOnClickListener(v -> {
            Intent intent = new Intent(this, ReceiptScannerActivity.class);
            intent.putExtra("launchedFromAddTransaction", true);
            scanReceiptLauncher.launch(intent);
        });

        binding.btnCurrencySelector.setOnClickListener(v -> {
            CurrencyPickerBottomSheet.newInstance(userCurrencyCode, currency -> {
                userCurrencyCode = currency.code;
                userCurrencySymbol = currency.symbol;
                binding.txtCurrencySymbolLarge.setText(userCurrencySymbol);
                loadRunningBalance();
            }).show(getSupportFragmentManager(), "CURRENCY_PICKER");
        });

        binding.btnSaveTransaction.setOnClickListener(v -> saveTransaction());
    }

    private void loadCategories() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        mFirestore.collection("users").document(uid).collection("categories")
                .whereEqualTo("type", transactionType)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        // Categories are empty (e.g. newly signed-in Google users). Seed defaults and reload.
                        DefaultDataSeeder.seedDefaultData(uid,
                                aVoid -> loadCategories(),
                                e -> {
                                    showError("Failed to seed categories: " + e.getLocalizedMessage());
                                    showLoading(false);
                                }
                        );
                    } else {
                        availableCategories.clear();
                        for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                            availableCategories.add(Category.fromDocument(doc));
                        }
                        categoryGridAdapter.setCategories(availableCategories);
                        selectedCategory = null;
                    }
                });
    }

    private void loadRunningBalance() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        mFirestore.collection("users").document(uid).collection("transactions").get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    double balance = 0;
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        Double amt = doc.getDouble("amount");
                        String type = doc.getString("type");
                        if (amt != null && type != null) {
                            if ("income".equalsIgnoreCase(type)) {
                                balance += amt;
                            } else {
                                balance -= amt;
                            }
                        }
                    }
                    binding.txtAvailableBalanceHint.setText(String.format("Available balance: %s",
                            CurrencyManager.getInstance().formatAmount(balance)));
                });
    }

    private void saveTransaction() {
        String amountStr = binding.etAmount.getText().toString().trim();
        if (amountStr.isEmpty()) {
            showError("Please enter an amount.");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            showError(getString(R.string.err_invalid_amount));
            return;
        }

        if (amount <= 0) {
            showError(getString(R.string.err_invalid_amount));
            return;
        }

        if (selectedCategory == null) {
            showError(getString(R.string.err_select_category));
            return;
        }

        String note = binding.etNote.getText().toString().trim();

        showLoading(true);
        saveCompleted = false;
        startSaveTimeout();

        String uid = mAuth.getCurrentUser().getUid();

        Transaction transaction = new Transaction(
                "",
                amount,
                transactionType,
                selectedCategory.getName(),
                note,
                selectedDate,
                selectedAccount,
                userCurrencyCode,
                userCurrencySymbol,
                Timestamp.now()
        );

        if (groupId != null && "expense".equals(transactionType)) {
            mFirestore.collection("groups").document(groupId).get()
                    .addOnSuccessListener(groupDoc -> {
                        if (!groupDoc.exists()) {
                            executeSaveTransaction(uid, transaction);
                            return;
                        }

                        Map<String, Object> memberLimits = (Map<String, Object>) groupDoc.get("memberLimits");
                        if (memberLimits != null && memberLimits.containsKey(uid)) {
                            double limit = 0.0;
                            Object limitObj = memberLimits.get(uid);
                            if (limitObj instanceof Number) {
                                limit = ((Number) limitObj).doubleValue();
                            }

                            final double finalLimit = limit;

                            Calendar startCal = Calendar.getInstance();
                            startCal.setTime(selectedDate);
                            startCal.set(Calendar.DAY_OF_MONTH, 1);
                            startCal.set(Calendar.HOUR_OF_DAY, 0);
                            startCal.set(Calendar.MINUTE, 0);
                            startCal.set(Calendar.SECOND, 0);
                            startCal.set(Calendar.MILLISECOND, 0);
                            Date startBound = startCal.getTime();

                            mFirestore.collection("groups").document(groupId).collection("group_transactions")
                                    .whereEqualTo("type", "expense")
                                    .whereEqualTo("userUid", uid)
                                    .get()
                                    .addOnSuccessListener(txnSnapshots -> {
                                        double totalSpent = 0.0;
                                        for (DocumentSnapshot doc : txnSnapshots.getDocuments()) {
                                            Timestamp dateStamp = doc.getTimestamp("date");
                                            if (dateStamp != null && (dateStamp.toDate().after(startBound) || dateStamp.toDate().equals(startBound))) {
                                                Double amt = doc.getDouble("amount");
                                                if (amt != null) totalSpent += amt;
                                            }
                                        }

                                        if (totalSpent + amount > finalLimit) {
                                            cancelSaveTimeout();
                                            showLoading(false);
                                            showError(String.format(Locale.US, "Exceeds monthly group spending limit of %s! (Spent: %s)",
                                                    CurrencyManager.getInstance().formatAmount(finalLimit),
                                                    CurrencyManager.getInstance().formatAmount(totalSpent)));
                                        } else {
                                            executeSaveTransaction(uid, transaction);
                                        }
                                    })
                                    .addOnFailureListener(e -> executeSaveTransaction(uid, transaction));
                        } else {
                            executeSaveTransaction(uid, transaction);
                        }
                    })
                    .addOnFailureListener(e -> executeSaveTransaction(uid, transaction));
        } else {
            executeSaveTransaction(uid, transaction);
        }
    }

    private void startSaveTimeout() {
        cancelSaveTimeout();
        saveTimeoutRunnable = () -> {
            if (!saveCompleted && !isFinishing() && !isDestroyed()) {
                saveCompleted = true;
                showLoading(false);
                showSuccess("Transaction saved!");
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::finish, 1000);
            }
        };
        timeoutHandler.postDelayed(saveTimeoutRunnable, SAVE_TIMEOUT_MS);
    }

    private void cancelSaveTimeout() {
        if (saveTimeoutRunnable != null) {
            timeoutHandler.removeCallbacks(saveTimeoutRunnable);
            saveTimeoutRunnable = null;
        }
    }

    private void executeSaveTransaction(String uid, Transaction transaction) {
        if (groupId == null) {
            TransactionRepository.getInstance().addTransaction(uid, transaction,
                    aVoid -> onSaveSuccess(uid, transaction),
                    e -> onSaveFailure(e.getLocalizedMessage())
            );
        } else {
            mFirestore.collection("users").document(uid).get()
                    .addOnSuccessListener(userDoc -> {
                        String name = "Member";
                        if (userDoc.exists()) {
                            String fetchedName = userDoc.getString("name");
                            if (fetchedName != null && !fetchedName.trim().isEmpty()) {
                                name = fetchedName;
                            }
                        }

                        final String finalName = name;
                        String txnId = mFirestore.collection("users").document(uid).collection("transactions").document().getId();
                        transaction.setId(txnId);

                        WriteBatch batch = mFirestore.batch();

                        DocumentReference userRef = mFirestore.collection("users").document(uid).collection("transactions").document(txnId);
                        batch.set(userRef, transaction.toMap());

                        DocumentReference groupRef = mFirestore.collection("groups").document(groupId).collection("group_transactions").document(txnId);
                        Map<String, Object> groupTxnMap = transaction.toMap();
                        groupTxnMap.put("userUid", uid);
                        groupTxnMap.put("userName", finalName);
                        batch.set(groupRef, groupTxnMap);

                        double delta = "income".equals(transaction.getType()) ? transaction.getAmount() : -transaction.getAmount();
                        DocumentReference groupDocRef = mFirestore.collection("groups").document(groupId);
                        batch.update(groupDocRef, "balance", com.google.firebase.firestore.FieldValue.increment(delta));

                        batch.commit()
                                .addOnSuccessListener(aVoid -> onSaveSuccess(uid, transaction))
                                .addOnFailureListener(e -> onSaveFailure(e.getLocalizedMessage()));
                    })
                    .addOnFailureListener(e -> onSaveFailure(e.getLocalizedMessage()));
        }
    }

    private void onSaveSuccess(String uid, Transaction transaction) {
        if (saveCompleted) return; // Already handled by timeout
        saveCompleted = true;
        cancelSaveTimeout();

        checkBudgetsAndNotify(uid, transaction);

        if ("expense".equalsIgnoreCase(transaction.getType())) {
            try {
                RoundUpSavingsManager.getInstance().processRoundUp(this, transaction.getAmount());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        try {
            AnomalyDetector.getInstance().checkForAnomaly(this, uid, transaction);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            GamificationManager.getInstance().updateDailyStreak(uid);
            GamificationManager.getInstance().checkAndAwardBadge(uid, "first_transaction");
        } catch (Exception e) {
            e.printStackTrace();
        }

        showLoading(false);
        showSuccess("Transaction saved!");
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::finish, 1000);
    }

    private void onSaveFailure(String errorMsg) {
        if (saveCompleted) return; // Already handled by timeout
        saveCompleted = true;
        cancelSaveTimeout();

        showLoading(false);
        showError("Failed to save transaction: " + errorMsg);
    }

    private void checkBudgetsAndNotify(String uid, Transaction transaction) {
        if (!"expense".equals(transaction.getType())) return;

        Calendar cal = Calendar.getInstance();
        cal.setTime(transaction.getDate());
        int month = cal.get(Calendar.MONTH);
        int year = cal.get(Calendar.YEAR);

        String categoryName = transaction.getCategory();

        // 1. Get Budget Limit for this Category
        mFirestore.collection("users").document(uid).collection("budgets")
                .whereEqualTo("category", categoryName)
                .whereEqualTo("month", month)
                .whereEqualTo("year", year)
                .get()
                .addOnSuccessListener(budgetSnapshots -> {
                    if (budgetSnapshots.isEmpty()) return;

                    DocumentSnapshot budgetDoc = budgetSnapshots.getDocuments().get(0);
                    Double limitAmount = budgetDoc.getDouble("limitAmount");
                    if (limitAmount == null || limitAmount <= 0) return;

                    // 2. Sum spent transactions for this Category this month
                    Calendar startCal = Calendar.getInstance();
                    startCal.set(year, month, 1, 0, 0, 0);
                    Date startBound = startCal.getTime();

                    mFirestore.collection("users").document(uid).collection("transactions")
                            .whereEqualTo("category", categoryName)
                            .whereEqualTo("type", "expense")
                            .get()
                            .addOnSuccessListener(txnSnapshots -> {
                                double totalSpent = 0;
                                for (DocumentSnapshot doc : txnSnapshots.getDocuments()) {
                                    Timestamp dateStamp = doc.getTimestamp("date");
                                    if (dateStamp != null && (dateStamp.toDate().after(startBound) || dateStamp.toDate().equals(startBound))) {
                                        Double amt = doc.getDouble("amount");
                                        if (amt != null) totalSpent += amt;
                                    }
                                }

                                double percentage = (totalSpent / limitAmount) * 100;
                                if (percentage >= 100) {
                                    sendLocalNotification("Budget Exceeded", 
                                            String.format("You have exceeded your monthly limit for %s. Spent: %s of Limit: %s", 
                                                    categoryName, 
                                                    CurrencyManager.getInstance().formatAmount(totalSpent), 
                                                    CurrencyManager.getInstance().formatAmount(limitAmount)));
                                } else if (percentage >= 80) {
                                    sendLocalNotification("Budget Alert", 
                                            String.format("You have used %.1f%% of your budget limit for %s. Spent: %s of Limit: %s", 
                                                    percentage, 
                                                    categoryName, 
                                                    CurrencyManager.getInstance().formatAmount(totalSpent), 
                                                    CurrencyManager.getInstance().formatAmount(limitAmount)));
                                }
                            });
                });
    }

    private void sendLocalNotification(String title, String message) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "BudgetAlerts";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Budget Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Monitors budget limits and triggers alerts when threshold exceeds.");
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());

        // Also save this notification inside Firestore collection for user notifications inbox
        saveNotificationToFirestore(title, message);
    }

    private void saveNotificationToFirestore(String title, String message) {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        java.util.Map<String, Object> notification = new java.util.HashMap<>();
        notification.put("title", title);
        notification.put("message", message);
        notification.put("type", "alert");
        notification.put("isRead", false);
        notification.put("createdAt", Timestamp.now());

        mFirestore.collection("users").document(uid).collection("notifications").add(notification);
    }

    private void checkVoicePermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startVoiceLogging();
        } else {
            requestRecordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void startVoiceLogging() {
        voiceLoggingHelper.startListening(this, new VoiceLoggingHelper.VoiceLoggingCallback() {
            @Override
            public void onListeningStarted() {
                showInfo("Listening... Speak now");
                binding.btnMic.setImageTintList(ColorStateList.valueOf(Color.RED));
            }

            @Override
            public void onListeningStopped() {
                binding.btnMic.setImageTintList(ColorStateList.valueOf(Color.parseColor("#7C6FE0")));
            }

            @Override
            public void onVoiceParsed(double amount, String categoryName, String type, String rawText) {
                showSuccess("Parsed: " + rawText);

                if (amount > 0) {
                    binding.etAmount.setText(String.valueOf((int) amount));
                }

                if ("income".equals(type)) {
                    binding.toggleTypeGroup.check(R.id.btnToggleIncome);
                    transactionType = "income";
                } else {
                    binding.toggleTypeGroup.check(R.id.btnToggleExpense);
                    transactionType = "expense";
                }

                if (mAuth.getCurrentUser() != null) {
                    mFirestore.collection("users").document(mAuth.getCurrentUser().getUid()).collection("categories")
                            .whereEqualTo("type", transactionType)
                            .get()
                            .addOnSuccessListener(queryDocumentSnapshots -> {
                                availableCategories.clear();
                                for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                                    availableCategories.add(Category.fromDocument(doc));
                                }
                                categoryGridAdapter.setCategories(availableCategories);
                                categoryGridAdapter.setSelectedCategoryByName(categoryName);
                                selectedCategory = categoryGridAdapter.getSelectedCategory();
                            });
                }
            }

            @Override
            public void onError(String errorMsg) {
                showError(errorMsg);
            }
        });
    }

    private void showInfo(String message) {
        Snackbar snackbar = Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG);
        snackbar.setBackgroundTint(getResources().getColor(R.color.colorPrimary));
        snackbar.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelSaveTimeout();
    }

    private void showLoading(boolean isLoading) {
        binding.loaderOverlay.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnSaveTransaction.setEnabled(!isLoading);
    }

    private void showError(String message) {
        Snackbar snackbar = Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG);
        snackbar.setBackgroundTint(getResources().getColor(R.color.colorDanger));
        snackbar.show();
    }

    private void showSuccess(String message) {
        Snackbar snackbar = Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG);
        snackbar.setBackgroundTint(getResources().getColor(R.color.colorSecondary));
        snackbar.show();
    }
}
