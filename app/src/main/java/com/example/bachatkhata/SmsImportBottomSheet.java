package com.example.bachatkhata;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.bachatkhata.databinding.LayoutSmsImportBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class SmsImportBottomSheet extends BottomSheetDialogFragment {

    private LayoutSmsImportBinding binding;
    private SmsParser.ParsedTransaction parsedTxn;
    private FirebaseFirestore mFirestore;
    private FirebaseAuth mAuth;

    private String transactionType = "expense";
    private Date selectedDate = new Date();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.US);
    
    private Runnable onDismissListener;

    public void setOnDismissListener(Runnable listener) {
        this.onDismissListener = listener;
    }

    @Override
    public void onDismiss(@NonNull android.content.DialogInterface dialog) {
        super.onDismiss(dialog);
        if (onDismissListener != null) {
            onDismissListener.run();
        }
    }

    public static SmsImportBottomSheet newInstance(SmsParser.ParsedTransaction txn) {
        SmsImportBottomSheet fragment = new SmsImportBottomSheet();
        Bundle args = new Bundle();
        args.putSerializable("parsed_transaction", txn);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            parsedTxn = (SmsParser.ParsedTransaction) getArguments().getSerializable("parsed_transaction");
        }
        mFirestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = LayoutSmsImportBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (parsedTxn == null) {
            dismiss();
            return;
        }

        selectedDate = parsedTxn.date != null ? parsedTxn.date : new Date();
        transactionType = parsedTxn.type != null ? parsedTxn.type : "expense";

        // Pre-fill inputs
        binding.etAmount.setText(String.format(Locale.US, "%.2f", parsedTxn.amount));
        binding.etNote.setText(parsedTxn.merchant);
        binding.txtDate.setText(dateFormat.format(selectedDate));
        
        String accountText = "UPI";
        if (parsedTxn.accountLast4 != null && !parsedTxn.accountLast4.equals("XXXX")) {
            accountText = "Bank Account (..." + parsedTxn.accountLast4 + ")";
        }
        binding.etAccount.setText(accountText);

        setupToggleGroup();
        setupDatePicker();
        
        if (mAuth.getCurrentUser() != null) {
            String initialCategory = SmsParser.mapMerchantToCategory(parsedTxn.merchant);
            loadCategories(transactionType, initialCategory);
        }

        binding.btnDiscard.setOnClickListener(v -> dismiss());
        binding.btnSave.setOnClickListener(v -> saveTransaction());
    }

    private void setupToggleGroup() {
        if ("income".equalsIgnoreCase(transactionType)) {
            binding.toggleTypeGroup.check(R.id.btnToggleIncome);
            binding.btnToggleIncome.setBackgroundColor(Color.parseColor("#5DCAA5")); // colorSecondary
            binding.btnToggleIncome.setTextColor(Color.WHITE);
            binding.btnToggleExpense.setBackgroundColor(Color.TRANSPARENT);
            binding.btnToggleExpense.setTextColor(Color.parseColor("#7C6FE0"));
        } else {
            binding.toggleTypeGroup.check(R.id.btnToggleExpense);
            binding.btnToggleExpense.setBackgroundColor(Color.parseColor("#E24B4A")); // colorDanger
            binding.btnToggleExpense.setTextColor(Color.WHITE);
            binding.btnToggleIncome.setBackgroundColor(Color.TRANSPARENT);
            binding.btnToggleIncome.setTextColor(Color.parseColor("#7C6FE0"));
        }

        binding.toggleTypeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnToggleIncome) {
                transactionType = "income";
                binding.btnToggleIncome.setBackgroundColor(Color.parseColor("#5DCAA5"));
                binding.btnToggleIncome.setTextColor(Color.WHITE);
                binding.btnToggleExpense.setBackgroundColor(Color.TRANSPARENT);
                binding.btnToggleExpense.setTextColor(Color.parseColor("#7C6FE0"));
            } else {
                transactionType = "expense";
                binding.btnToggleExpense.setBackgroundColor(Color.parseColor("#E24B4A"));
                binding.btnToggleExpense.setTextColor(Color.WHITE);
                binding.btnToggleIncome.setBackgroundColor(Color.TRANSPARENT);
                binding.btnToggleIncome.setTextColor(Color.parseColor("#7C6FE0"));
            }
            loadCategories(transactionType, "");
        });
    }

    private void setupDatePicker() {
        binding.cardDatePicker.setOnClickListener(v -> {
            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Select Transaction Date")
                    .setSelection(selectedDate.getTime())
                    .build();

            datePicker.addOnPositiveButtonClickListener(selection -> {
                selectedDate = new Date(selection);
                binding.txtDate.setText(dateFormat.format(selectedDate));
            });

            datePicker.show(getChildFragmentManager(), "DATE_PICKER");
        });
    }

    private void loadCategories(String type, String selectedCategoryName) {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        mFirestore.collection("users").document(uid).collection("categories")
                .whereEqualTo("type", type)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (binding == null) return;
                    if (queryDocumentSnapshots.isEmpty()) {
                        DefaultDataSeeder.seedDefaultData(uid,
                                aVoid -> loadCategories(type, selectedCategoryName),
                                e -> Toast.makeText(getContext(), "Failed to seed categories: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show()
                        );
                        return;
                    }
                    binding.chipGroupCategory.removeAllViews();
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        String catName = doc.getString("name");
                        if (catName != null) {
                            Chip chip = (Chip) LayoutInflater.from(getContext())
                                    .inflate(R.layout.item_category_chip, binding.chipGroupCategory, false);
                            chip.setText(catName);
                            chip.setId(View.generateViewId());
                            binding.chipGroupCategory.addView(chip);
                            if (catName.equalsIgnoreCase(selectedCategoryName)) {
                                chip.setChecked(true);
                            }
                        }
                    }
                });
    }

    private void saveTransaction() {
        String amountStr = binding.etAmount.getText().toString().trim();
        if (amountStr.isEmpty()) {
            Toast.makeText(getContext(), "Please enter an amount", Toast.LENGTH_SHORT).show();
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), "Invalid amount", Toast.LENGTH_SHORT).show();
            return;
        }

        if (amount <= 0) {
            Toast.makeText(getContext(), "Amount must be greater than zero", Toast.LENGTH_SHORT).show();
            return;
        }

        // Find checked category chip
        int checkedChipId = binding.chipGroupCategory.getCheckedChipId();
        if (checkedChipId == View.NO_ID) {
            Toast.makeText(getContext(), "Please select a category", Toast.LENGTH_SHORT).show();
            return;
        }
        Chip checkedChip = binding.chipGroupCategory.findViewById(checkedChipId);
        String categoryName = checkedChip.getText().toString();

        String note = binding.etNote.getText().toString().trim();
        String account = binding.etAccount.getText().toString().trim();
        if (account.isEmpty()) {
            account = "Bank";
        }

        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        Transaction transaction = new Transaction(
                "",
                amount,
                transactionType,
                categoryName,
                note,
                selectedDate,
                account,
                CurrencyManager.getInstance().getCurrentCurrencyCode(),
                CurrencyManager.getInstance().getCurrentCurrencySymbol(),
                Timestamp.now()
        );
        // Tag transaction as auto-imported from SMS
        transaction.setNote(note + " (Auto-Imported)");

        TransactionRepository.getInstance().addTransaction(uid, transaction,
                aVoid -> {
                    checkBudgetsAndNotify(uid, transaction);
                    if ("expense".equalsIgnoreCase(transaction.getType())) {
                        try {
                            RoundUpSavingsManager.getInstance().processRoundUp(getContext(), transaction.getAmount());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    if (getActivity() instanceof BaseActivity) {
                        ((BaseActivity) getActivity()).showSnackbar("Saved from SMS!", "SUCCESS");
                    } else {
                        Toast.makeText(getContext(), "Saved from SMS!", Toast.LENGTH_SHORT).show();
                    }
                    dismiss();
                },
                e -> Toast.makeText(getContext(), "Failed to save: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show()
        );
    }

    private void checkBudgetsAndNotify(String uid, Transaction transaction) {
        if (!"expense".equals(transaction.getType())) return;

        Calendar cal = Calendar.getInstance();
        cal.setTime(transaction.getDate());
        int month = cal.get(Calendar.MONTH);
        int year = cal.get(Calendar.YEAR);

        String categoryName = transaction.getCategory();

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
        if (getContext() == null) return;
        // Respect the "Enable Notifications" master switch.
        if (!NotificationSettings.isEnabled(getContext())) {
            return;
        }
        NotificationManager notificationManager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "budget_alerts";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Budget Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext(), channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
