package com.example.bachatkhata;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.bachatkhata.databinding.ActivityTransactionDetailBinding;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TransactionDetailActivity extends BaseActivity {

    private ActivityTransactionDetailBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;

    private Transaction transaction;
    private boolean isEditMode = false;
    private Category selectedCategory = null;
    private Date selectedDate;
    private String selectedAccount;

    private CategoryGridAdapter categoryGridAdapter;
    private final List<Category> availableCategories = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTransactionDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();

        if (getIntent().hasExtra("transaction")) {
            transaction = (Transaction) getIntent().getSerializableExtra("transaction");
        }

        if (getIntent().getBooleanExtra("isEditMode", false)) {
            isEditMode = true;
        }

        if (transaction == null) {
            finish();
            return;
        }

        selectedDate = transaction.getDate() != null ? transaction.getDate() : new Date();
        selectedAccount = transaction.getAccount() != null ? transaction.getAccount() : "Cash";

        setupRecyclerView();
        setupListeners();
        updateUI();

        if (isEditMode) {
            enterEditMode();
        }
    }

    private void setupRecyclerView() {
        categoryGridAdapter = new CategoryGridAdapter(category -> {
            selectedCategory = category;
        });
        binding.rvEditCategoryGrid.setAdapter(categoryGridAdapter);
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnEditToggle.setOnClickListener(v -> {
            if (isEditMode) {
                saveChanges();
            } else {
                enterEditMode();
            }
        });

        binding.btnDelete.setOnClickListener(v -> {
            if (isEditMode) {
                // In edit mode, clicking the bottom button discards changes (Cancel)
                exitEditMode();
            } else {
                // In read mode, delete
                showDeleteConfirmationDialog();
            }
        });

        binding.cardEditDatePicker.setOnClickListener(v -> {
            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Select Date")
                    .setSelection(selectedDate.getTime())
                    .build();

            datePicker.addOnPositiveButtonClickListener(selection -> {
                selectedDate = new Date(selection);
                binding.txtEditDate.setText(dateFormat.format(selectedDate));
            });

            datePicker.show(getSupportFragmentManager(), "DATE_PICKER");
        });

        binding.cardEditAccountSelector.setOnClickListener(v -> {
            AccountPickerBottomSheet.newInstance(accountName -> {
                selectedAccount = accountName;
                binding.txtEditAccount.setText(accountName);
            }).show(getSupportFragmentManager(), "ACCOUNT_PICKER");
        });
    }

    private void updateUI() {
        // Read Mode UI
        binding.txtHeroEmoji.setText(getCategoryEmoji(transaction.getCategory()));
        
        String formattedAmount = CurrencyManager.getInstance().formatAmount(transaction.getAmount());
        if ("income".equalsIgnoreCase(transaction.getType())) {
            binding.txtHeroAmount.setText(String.format("+%s", formattedAmount));
            binding.txtHeroAmount.setTextColor(Color.parseColor("#3DAF85"));
            binding.txtDetailType.setText("Income");
            binding.txtDetailType.setTextColor(Color.parseColor("#3DAF85"));
        } else {
            binding.txtHeroAmount.setText(String.format("-%s", formattedAmount));
            binding.txtHeroAmount.setTextColor(Color.parseColor("#E24B4A"));
            binding.txtDetailType.setText("Expense");
            binding.txtDetailType.setTextColor(Color.parseColor("#E24B4A"));
        }

        binding.txtHeroCategory.setText(transaction.getCategory());
        binding.txtDetailDate.setText(transaction.getDate() != null ? dateFormat.format(transaction.getDate()) : "");
        binding.txtDetailAccount.setText(transaction.getAccount());
        binding.txtDetailCurrency.setText(String.format("%s (%s)", transaction.getCurrency(), transaction.getCurrencySymbol()));

        // Discount breakdown, when the row carries one. The saving stays visible
        // long after the purchase — that is the point of storing it.
        if (transaction.hasDiscount()) {
            CurrencyManager currency = CurrencyManager.getInstance();
            binding.txtDetailOriginalAmount.setText(
                    currency.formatAmount(transaction.getOriginalAmount()));
            binding.txtDetailDiscountAmount.setText(String.format("-%s",
                    currency.formatAmount(transaction.getDiscountAmount())));
            binding.layoutDiscountDetail.setVisibility(View.VISIBLE);
        } else {
            binding.layoutDiscountDetail.setVisibility(View.GONE);
        }

        String note = transaction.getNote();
        if (note == null || note.trim().isEmpty()) {
            binding.txtDetailNote.setText("No note details.");
            binding.txtDetailNote.setTextColor(Color.parseColor("#9A96B8")); // colorTextSecondary dark
        } else {
            binding.txtDetailNote.setText(note);
            binding.txtDetailNote.setTextColor(Color.parseColor("#1A1A2E")); // colorTextPrimary
        }
    }

    private void enterEditMode() {
        isEditMode = true;
        binding.layoutReadMode.setVisibility(View.GONE);
        binding.layoutEditMode.setVisibility(View.VISIBLE);

        // Populate Edit fields
        binding.etEditAmount.setText(String.valueOf(transaction.getAmount()));
        binding.etEditNote.setText(transaction.getNote());
        binding.txtEditDate.setText(dateFormat.format(selectedDate));
        binding.txtEditAccount.setText(selectedAccount);

        // Edit Toolbar icon check representation
        binding.btnEditToggle.setImageResource(R.drawable.ic_check);
        binding.btnEditToggle.setImageTintList(ColorStateList.valueOf(Color.parseColor("#3DAF85"))); // green color

        // Bottom button becomes Cancel Outlined Danger
        binding.btnDelete.setText("Cancel");
        binding.btnDelete.setTextColor(Color.parseColor("#6B6B8A"));
        binding.btnDelete.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#6B6B8A")));

        loadCategoriesAndSelectCurrent();
    }

    private void exitEditMode() {
        isEditMode = false;
        binding.layoutReadMode.setVisibility(View.VISIBLE);
        binding.layoutEditMode.setVisibility(View.GONE);

        // Toolbar icon edits
        binding.btnEditToggle.setImageResource(R.drawable.ic_more);
        binding.btnEditToggle.setImageTintList(ColorStateList.valueOf(Color.parseColor("#7C6FE0")));

        // Bottom button becomes Delete Outlined Danger
        binding.btnDelete.setText("Delete Transaction");
        binding.btnDelete.setTextColor(Color.parseColor("#E24B4A"));
        binding.btnDelete.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#E24B4A")));

        // Reset variables
        selectedDate = transaction.getDate();
        selectedAccount = transaction.getAccount();
        updateUI();
    }

    private void loadCategoriesAndSelectCurrent() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        binding.loaderOverlay.setVisibility(View.VISIBLE);
        mFirestore.collection("users").document(uid).collection("categories")
                .whereEqualTo("type", transaction.getType())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    binding.loaderOverlay.setVisibility(View.GONE);
                    availableCategories.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        Category c = Category.fromDocument(doc);
                        // Archived categories stay hidden, except the one this transaction
                        // already uses — otherwise editing it would silently reclassify it.
                        if (c.getArchived() && !c.getName().equals(transaction.getCategory())) continue;
                        availableCategories.add(c);
                    }
                    categoryGridAdapter.setCategories(availableCategories);
                    categoryGridAdapter.setSelectedCategoryByName(transaction.getCategory());
                })
                .addOnFailureListener(e -> {
                    binding.loaderOverlay.setVisibility(View.GONE);
                });
    }

    private void saveChanges() {
        String amountStr = binding.etEditAmount.getText().toString().trim();
        if (amountStr.isEmpty()) {
            Snackbar.make(binding.getRoot(), "Please enter an amount.", Snackbar.LENGTH_SHORT).show();
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            Snackbar.make(binding.getRoot(), "Invalid amount.", Snackbar.LENGTH_SHORT).show();
            return;
        }

        if (amount <= 0) {
            Snackbar.make(binding.getRoot(), "Amount must be greater than zero.", Snackbar.LENGTH_SHORT).show();
            return;
        }

        Category cat = categoryGridAdapter.getSelectedCategory();
        if (cat != null) {
            selectedCategory = cat;
        }

        String note = binding.etEditNote.getText().toString().trim();

        showLoading(true);
        String uid = mAuth.getCurrentUser().getUid();

        // Editing the amount invalidates any stored discount breakdown: the old
        // original price and saving no longer describe this number, and keeping
        // them would render a "you saved" line that is simply untrue.
        boolean discountDropped = transaction.hasDiscount() && amount != transaction.getAmount();
        if (discountDropped) {
            transaction.clearDiscount();
        }

        transaction.setAmount(amount);
        if (selectedCategory != null) {
            transaction.setCategory(selectedCategory.getName());
        }
        transaction.setNote(note);
        transaction.setDate(selectedDate);
        transaction.setAccount(selectedAccount);

        TransactionRepository.getInstance().updateTransaction(uid, transaction,
                aVoid -> {
                    showLoading(false);
                    exitEditMode();
                    Snackbar.make(binding.getRoot(),
                            discountDropped
                                    ? getString(R.string.discount_cleared_on_edit)
                                    : "Changes saved!",
                            Snackbar.LENGTH_SHORT).show();
                },
                e -> {
                    showLoading(false);
                    Snackbar.make(binding.getRoot(), "Failed to save: " + e.getLocalizedMessage(), Snackbar.LENGTH_LONG).show();
                });
    }

    private void showDeleteConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Transaction")
                .setMessage("Are you sure you want to delete this transaction?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (mAuth.getCurrentUser() != null) {
                        showLoading(true);
                        TransactionRepository.getInstance().deleteTransaction(
                                mAuth.getCurrentUser().getUid(),
                                transaction.getId(),
                                aVoid -> {
                                    showLoading(false);
                                    finish();
                                },
                                e -> {
                                    showLoading(false);
                                    Snackbar.make(binding.getRoot(), "Failed to delete: " + e.getLocalizedMessage(), Snackbar.LENGTH_LONG).show();
                                }
                        );
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String getCategoryEmoji(String category) {
        if (category == null) return "📦";
        switch (category.toLowerCase()) {
            case "food": return "🍛";
            case "transport": return "🚌";
            case "shopping": return "🛍️";
            case "bills": return "💡";
            case "health": return "🏥";
            case "entertainment": return "🎮";
            case "education": return "📚";
            case "rent": return "🏠";
            case "personal care": return "💆";
            case "travel": return "✈️";
            case "gifts": return "🎁";
            case "salary": return "💰";
            case "freelance": return "💻";
            default: return "📦";
        }
    }

    private void showLoading(boolean isLoading) {
        binding.loaderOverlay.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }
}
