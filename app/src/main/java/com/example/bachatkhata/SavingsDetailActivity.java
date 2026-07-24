package com.example.bachatkhata;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bachatkhata.databinding.ActivitySavingsDetailBinding;
import com.example.bachatkhata.databinding.ItemCategoryGridCellBinding;
import com.example.bachatkhata.databinding.ItemDepositHistoryBinding;
import com.example.bachatkhata.domain.BucketConfig;
import com.example.bachatkhata.domain.BucketType;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SavingsDetailActivity extends BaseActivity {

    private ActivitySavingsDetailBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;

    private SavingsGoal goal;
    private boolean isEditMode = false;

    // Edit state variables
    private String selectedEmoji = "💰";
    private Date selectedDeadline = null;

    private final List<DocumentSnapshot> depositHistory = new ArrayList<>();
    private DepositAdapter adapter;

    private final SimpleDateFormat displayDateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.US);
    private final SimpleDateFormat monthYearFormat = new SimpleDateFormat("MMM yyyy", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySavingsDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();

        goal = (SavingsGoal) getIntent().getSerializableExtra("savings_goal");
        if (goal == null) {
            finish();
            return;
        }

        selectedEmoji = goal.getIcon();
        if (goal.getDeadline() != null) {
            selectedDeadline = goal.getDeadline().toDate();
        }

        setupRecyclerView();
        setupListeners();
        renderGoalDetails();
        loadDepositHistory();
    }

    private void setupRecyclerView() {
        adapter = new DepositAdapter();
        binding.rvDepositHistory.setLayoutManager(new LinearLayoutManager(this));
        binding.rvDepositHistory.setAdapter(adapter);
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnEditToggle.setOnClickListener(v -> toggleEditMode());

        binding.btnActionSubmit.setOnClickListener(v -> {
            if (isEditMode) {
                saveGoalChanges();
            } else {
                showAddFundsDialog();
            }
        });

        binding.btnDeleteGoal.setOnClickListener(v -> confirmDeleteGoal());

        // Setup Date Picker for Edit Mode
        binding.cardEditDeadlinePicker.setOnClickListener(v -> {
            long initialSelection = selectedDeadline != null ? selectedDeadline.getTime() : System.currentTimeMillis();
            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Select Target Date")
                    .setSelection(initialSelection)
                    .build();

            datePicker.addOnPositiveButtonClickListener(selection -> {
                selectedDeadline = new Date(selection);
                binding.txtEditDeadlineDate.setText(displayDateFormat.format(selectedDeadline));
            });

            datePicker.show(getSupportFragmentManager(), "EDIT_DEADLINE_PICKER");
        });
    }

    private void toggleEditMode() {
        isEditMode = !isEditMode;
        if (isEditMode) {
            // Enter Edit Mode
            binding.txtTitle.setText("Edit Goal");
            binding.layoutReadMode.setVisibility(View.GONE);
            binding.layoutEditMode.setVisibility(View.VISIBLE);
            binding.btnActionSubmit.setText("Save Changes");
            binding.btnDeleteGoal.setVisibility(View.GONE);

            // Populates fields
            binding.etEditGoalName.setText(goal.getName());
            binding.etEditTargetAmount.setText(String.valueOf(goal.getTargetAmount()));
            if (selectedDeadline != null) {
                binding.txtEditDeadlineDate.setText(displayDateFormat.format(selectedDeadline));
            } else {
                binding.txtEditDeadlineDate.setText("Select Target Date (Optional)");
            }

            setupEditEmojiPicker();
        } else {
            // Cancel Edit Mode
            binding.txtTitle.setText("Goal Details");
            binding.layoutReadMode.setVisibility(View.VISIBLE);
            binding.layoutEditMode.setVisibility(View.GONE);
            binding.btnActionSubmit.setText("Add Funds");
            binding.btnDeleteGoal.setVisibility(View.VISIBLE);
            renderGoalDetails();
        }
    }

    private void setupEditEmojiPicker() {
        List<String> emojis = new ArrayList<>();
        emojis.add("💰");
        emojis.add("🏠");
        emojis.add("🚗");
        emojis.add("✈️");
        emojis.add("📱");
        emojis.add("💻");
        emojis.add("🎓");
        emojis.add("💍");
        emojis.add("🏖️");
        emojis.add("🎮");
        emojis.add("🧸");
        emojis.add("🚲");
        emojis.add("🏍️");
        emojis.add("🏦");
        emojis.add("🛍️");
        emojis.add("🎁");

        EmojiAdapter emojiAdapter = new EmojiAdapter(emojis, goal.getIcon(), emoji -> selectedEmoji = emoji);
        binding.rvEditEmojiPicker.setLayoutManager(new GridLayoutManager(this, 4));
        binding.rvEditEmojiPicker.setAdapter(emojiAdapter);
    }

    private void renderGoalDetails() {
        binding.txtHeroEmoji.setText(goal.getIcon() != null ? goal.getIcon() : "💰");
        binding.txtHeroName.setText(goal.getName());

        double saved = goal.getSavedAmount();
        double target = goal.getTargetAmount();
        double remaining = Math.max(0, target - saved);

        double pct = (saved / target) * 100;
        binding.progressRing.setProgress((float) Math.min(pct, 100));
        binding.txtHeroPercent.setText(String.format(Locale.US, "%.1f%% Completed", pct));

        binding.txtDetailTarget.setText(CurrencyManager.getInstance().formatAmount(target));
        binding.txtDetailSaved.setText(CurrencyManager.getInstance().formatAmount(saved));
        binding.txtDetailRemaining.setText(CurrencyManager.getInstance().formatAmount(remaining));

        if (goal.getDeadline() != null) {
            Date deadlineDate = goal.getDeadline().toDate();
            binding.txtDetailDeadline.setText(displayDateFormat.format(deadlineDate));

            // Calculate Monthly Recommended savings
            long diffMs = deadlineDate.getTime() - System.currentTimeMillis();
            double months = diffMs / (1000.0 * 60 * 60 * 24 * 30.44);
            if (months <= 0) months = 1;

            if (remaining > 0) {
                double needed = remaining / months;
                binding.txtDetailMonthlyNeeded.setText(String.format("%s/month needed to reach goal",
                        CurrencyManager.getInstance().formatAmount(needed)));
                binding.layoutRecommendedSaving.setVisibility(View.VISIBLE);
            } else {
                binding.layoutRecommendedSaving.setVisibility(View.GONE);
            }
        } else {
            binding.txtDetailDeadline.setText("No Deadline");
            binding.layoutRecommendedSaving.setVisibility(View.GONE);
        }

        if (saved >= target) {
            binding.btnActionSubmit.setVisibility(View.GONE);
            binding.txtHeroPercent.setText("Completed!");
            binding.txtHeroPercent.setTextColor(Color.parseColor("#3DAF85"));
        } else {
            binding.btnActionSubmit.setVisibility(View.VISIBLE);
            binding.txtHeroPercent.setTextColor(Color.parseColor("#7C6FE0"));
        }
    }

    private void loadDepositHistory() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        mFirestore.collection("users").document(uid)
                .collection("savings_goals").document(goal.getId())
                .collection("transactions")
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    depositHistory.clear();
                    depositHistory.addAll(queryDocumentSnapshots.getDocuments());
                    if (depositHistory.isEmpty()) {
                        binding.txtEmptyDeposits.setVisibility(View.VISIBLE);
                        binding.rvDepositHistory.setVisibility(View.GONE);
                    } else {
                        binding.txtEmptyDeposits.setVisibility(View.GONE);
                        binding.rvDepositHistory.setVisibility(View.VISIBLE);
                    }
                    adapter.notifyDataSetChanged();
                });
    }

    private void showAddFundsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(String.format("Add Funds to %s", goal.getName()));

        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = params.rightMargin = params.topMargin = params.bottomMargin = 48;

        EditText input = new EditText(this);
        input.setHint("Enter amount to add");
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setLayoutParams(params);
        container.addView(input);
        builder.setView(container);

        builder.setPositiveButton("Add", (dialog, which) -> {
            String amountStr = input.getText().toString().trim();
            if (amountStr.isEmpty()) return;
            try {
                double amount = Double.parseDouble(amountStr);
                if (amount > 0) {
                    addFundsToGoal(amount);
                }
            } catch (Exception ignored) {}
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void addFundsToGoal(double amount) {
        if (mAuth.getCurrentUser() == null) return;
        showLoading(true);
        String uid = mAuth.getCurrentUser().getUid();

        double updatedAmount = goal.getSavedAmount() + amount;

        Map<String, Object> transaction = new HashMap<>();
        transaction.put("amount", amount);
        transaction.put("date", Timestamp.now());
        transaction.put("type", "deposit");

        mFirestore.collection("users").document(uid)
                .collection("savings_goals").document(goal.getId())
                .update("savedAmount", updatedAmount)
                .addOnSuccessListener(aVoid -> {
                    // Log subcollection
                    mFirestore.collection("users").document(uid)
                            .collection("savings_goals").document(goal.getId())
                            .collection("transactions").add(transaction);

                    logDepositToLedger(uid, amount);

                    // Add Notification
                    saveNotificationToFirestore(
                            "Funds Added",
                            String.format("Added %s to savings goal %s",
                                    CurrencyManager.getInstance().formatAmount(amount), goal.getName())
                    );

                    goal.setSavedAmount(updatedAmount);
                    showLoading(false);
                    showSuccess("Progress updated!");
                    renderGoalDetails();
                    loadDepositHistory();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    showError("Failed to update savings: " + e.getMessage());
                });
    }

    /**
     * Mirrors a goal deposit into the main ledger as an expense, under the category that
     * matches the goal's bucket. Without this the money leaves your spendable balance but
     * appears nowhere in totals, budgets or the Budgeting Rule — a phone fund would look
     * free, and an emergency fund wouldn't count as investing.
     *
     * <p>Goals with no bucket set fall back to "Investment", exactly as before.
     */
    private void logDepositToLedger(String uid, double amount) {
        String category = BucketConfig.savingsDepositCategory(BucketType.fromKey(goal.getBucket()));

        Transaction t = new Transaction();
        t.setAmount(amount);
        t.setType("expense");
        t.setCategory(category);
        t.setNote("Deposit to " + goal.getName());
        t.setDate(new java.util.Date());
        t.setAccount("Savings");
        t.setCurrency(CurrencyManager.getInstance().getCurrentCurrencyCode());
        t.setCurrencySymbol(CurrencyManager.getInstance().getCurrentCurrencySymbol());
        t.setSource("savings");
        t.setCreatedAt(Timestamp.now());

        TransactionRepository.getInstance().addTransaction(uid, t,
                aVoid -> { /* balance and reports pick it up on their next read */ },
                e -> showError("Deposit saved, but it couldn't be added to your ledger: "
                        + e.getLocalizedMessage()));
    }

    private void saveGoalChanges() {
        String name = binding.etEditGoalName.getText().toString().trim();
        String amountStr = binding.etEditTargetAmount.getText().toString().trim();

        if (name.isEmpty()) {
            showError("Goal name cannot be empty.");
            return;
        }

        if (amountStr.isEmpty()) {
            showError("Target amount cannot be empty.");
            return;
        }

        double targetAmount;
        try {
            targetAmount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            showError("Invalid target amount.");
            return;
        }

        if (targetAmount <= 0) {
            showError("Target amount must be greater than zero.");
            return;
        }

        if (mAuth.getCurrentUser() == null) return;
        showLoading(true);
        String uid = mAuth.getCurrentUser().getUid();

        Timestamp deadlineTs = selectedDeadline != null ? new Timestamp(selectedDeadline) : null;

        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("targetAmount", targetAmount);
        updates.put("icon", selectedEmoji);
        updates.put("deadline", deadlineTs);

        mFirestore.collection("users").document(uid)
                .collection("savings_goals").document(goal.getId())
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    goal.setName(name);
                    goal.setTargetAmount(targetAmount);
                    goal.setIcon(selectedEmoji);
                    goal.setDeadline(deadlineTs);

                    showLoading(false);
                    showSuccess("Goal updated!");
                    toggleEditMode(); // exits edit mode
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    showError("Failed to update goal: " + e.getMessage());
                });
    }

    private void confirmDeleteGoal() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Savings Goal")
                .setMessage("Are you sure you want to delete this savings goal? All deposit history records for this goal will be lost.")
                .setPositiveButton("Delete", (dialog, which) -> deleteGoal())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteGoal() {
        if (mAuth.getCurrentUser() == null) return;
        showLoading(true);
        String uid = mAuth.getCurrentUser().getUid();

        mFirestore.collection("users").document(uid)
                .collection("savings_goals").document(goal.getId())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    showSuccess("Goal deleted.");
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::finish, 800);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    showError("Failed to delete goal: " + e.getMessage());
                });
    }

    private void saveNotificationToFirestore(String title, String message) {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        Map<String, Object> notification = new HashMap<>();
        notification.put("title", title);
        notification.put("message", message);
        notification.put("type", "success");
        notification.put("isRead", false);
        notification.put("createdAt", Timestamp.now());

        mFirestore.collection("users").document(uid).collection("notifications").add(notification);
    }

    private void showLoading(boolean isLoading) {
        binding.loaderOverlay.setVisibility(isLoading ? View.VISIBLE : View.GONE);
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

    // RecyclerView Adapter for Deposit History
    private class DepositAdapter extends RecyclerView.Adapter<DepositAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemDepositHistoryBinding itemBinding = ItemDepositHistoryBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DocumentSnapshot doc = depositHistory.get(position);
            Double amount = doc.getDouble("amount");
            Timestamp timestamp = doc.getTimestamp("date");

            double amt = amount != null ? amount : 0;
            holder.binding.txtAmount.setText(String.format("+%s", CurrencyManager.getInstance().formatAmount(amt)));
            if (timestamp != null) {
                holder.binding.txtDate.setText(displayDateFormat.format(timestamp.toDate()));
            } else {
                holder.binding.txtDate.setText("");
            }
        }

        @Override
        public int getItemCount() {
            return depositHistory.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final ItemDepositHistoryBinding binding;

            public ViewHolder(ItemDepositHistoryBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    // Emoji Picker Adapter for Edit Mode
    private static class EmojiAdapter extends RecyclerView.Adapter<EmojiAdapter.ViewHolder> {

        private final List<String> emojis;
        private int selectedPosition = -1;
        private final EmojiAdapter.OnEmojiClickListener listener;

        public interface OnEmojiClickListener {
            void onEmojiClick(String emoji);
        }

        public EmojiAdapter(List<String> emojis, String currentIcon, EmojiAdapter.OnEmojiClickListener listener) {
            this.emojis = emojis;
            this.listener = listener;

            for (int i = 0; i < emojis.size(); i++) {
                if (emojis.get(i).equals(currentIcon)) {
                    selectedPosition = i;
                    break;
                }
            }
            if (selectedPosition == -1) {
                selectedPosition = 0;
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemCategoryGridCellBinding binding = ItemCategoryGridCellBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(emojis.get(position), position == selectedPosition);
        }

        @Override
        public int getItemCount() {
            return emojis.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final ItemCategoryGridCellBinding binding;

            public ViewHolder(ItemCategoryGridCellBinding binding) {
                super(binding.getRoot());
                this.binding = binding;

                itemView.setOnClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        notifyItemChanged(selectedPosition);
                        selectedPosition = pos;
                        notifyItemChanged(selectedPosition);
                        if (listener != null) {
                            listener.onEmojiClick(emojis.get(pos));
                        }
                    }
                });
            }

            public void bind(String emoji, boolean isSelected) {
                binding.txtCategoryName.setVisibility(View.GONE);
                binding.txtCategoryEmoji.setText(emoji);

                int baseColor = Color.parseColor("#7C6FE0");

                if (isSelected) {
                    binding.layoutIconCircle.setBackgroundColor(baseColor);
                    binding.txtCategoryEmoji.setTextColor(Color.WHITE);
                    binding.cardCategoryIcon.setStrokeColor(baseColor);
                    binding.cardCategoryIcon.setStrokeWidth(6);
                } else {
                    int alphaColor = Color.argb(30, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor));
                    binding.layoutIconCircle.setBackgroundColor(alphaColor);
                    binding.txtCategoryEmoji.setTextColor(baseColor);
                    binding.cardCategoryIcon.setStrokeColor(Color.parseColor("#E0DEFF"));
                    binding.cardCategoryIcon.setStrokeWidth(3);
                }
            }
        }
    }
}
