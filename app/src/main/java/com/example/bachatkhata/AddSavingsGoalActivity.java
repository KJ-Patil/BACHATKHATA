package com.example.bachatkhata;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bachatkhata.databinding.ActivityAddSavingsGoalBinding;
import com.example.bachatkhata.databinding.ItemCategoryGridCellBinding;
import com.example.bachatkhata.domain.BucketType;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AddSavingsGoalActivity extends BaseActivity {

    private ActivityAddSavingsGoalBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;

    private String selectedEmoji = "💰"; // Default emoji
    private Date selectedDeadline = null;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddSavingsGoalBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();

        setupEmojiPicker();
        setupDatePicker();
        setupListeners();
    }

    private void setupEmojiPicker() {
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

        EmojiAdapter adapter = new EmojiAdapter(emojis, emoji -> selectedEmoji = emoji);
        binding.rvEmojiPicker.setLayoutManager(new GridLayoutManager(this, 4));
        binding.rvEmojiPicker.setAdapter(adapter);
    }

    private void setupDatePicker() {
        binding.cardDeadlinePicker.setOnClickListener(v -> {
            long initialSelection = selectedDeadline != null ? selectedDeadline.getTime() : System.currentTimeMillis();
            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Select Target Date")
                    .setSelection(initialSelection)
                    .build();

            datePicker.addOnPositiveButtonClickListener(selection -> {
                selectedDeadline = new Date(selection);
                binding.txtDeadlineDate.setText(dateFormat.format(selectedDeadline));
            });

            datePicker.show(getSupportFragmentManager(), "DEADLINE_PICKER");
        });
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnCreateGoal.setOnClickListener(v -> createSavingsGoal());
    }

    private void createSavingsGoal() {
        String name = binding.etGoalName.getText().toString().trim();
        String amountStr = binding.etTargetAmount.getText().toString().trim();

        if (name.isEmpty()) {
            showError("Please enter a goal name.");
            return;
        }

        if (amountStr.isEmpty()) {
            showError("Please enter a target amount.");
            return;
        }

        double targetAmount;
        try {
            targetAmount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            showError("Please enter a valid target amount.");
            return;
        }

        if (targetAmount <= 0) {
            showError("Target amount must be greater than zero.");
            return;
        }

        if (mAuth.getCurrentUser() == null) {
            showError("User not logged in.");
            return;
        }

        showLoading(true);
        String uid = mAuth.getCurrentUser().getUid();

        DocumentReference docRef = mFirestore.collection("users")
                .document(uid)
                .collection("savings_goals")
                .document();

        String id = docRef.getId();
        String currencyCode = CurrencyManager.getInstance().getCurrentCurrencyCode();
        Timestamp deadlineTs = selectedDeadline != null ? new Timestamp(selectedDeadline) : null;

        SavingsGoal goal = new SavingsGoal(
                id,
                name,
                selectedEmoji,
                targetAmount,
                0.0, // starts with zero saved
                currencyCode,
                deadlineTs,
                Timestamp.now()
        );
        goal.setBucket(selectedBucket().key());

        docRef.set(goal.toMap())
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    showSuccess("Savings goal created successfully!");
                    // Give a brief delay for user to read success message before finishing
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::finish, 1000);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    showError("Failed to create goal: " + e.getMessage());
                });
    }

    /** Defaults to Investments, matching how untagged goals have always been treated. */
    private BucketType selectedBucket() {
        int checkedId = binding.chipGroupBucket.getCheckedChipId();
        if (checkedId == R.id.chipBucketNeeds) return BucketType.NEEDS;
        if (checkedId == R.id.chipBucketWants) return BucketType.WANTS;
        return BucketType.INVESTMENTS;
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

    private static class EmojiAdapter extends RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder> {

        private final List<String> emojis;
        private int selectedPosition = 0;
        private final OnEmojiClickListener listener;

        public interface OnEmojiClickListener {
            void onEmojiClick(String emoji);
        }

        public EmojiAdapter(List<String> emojis, OnEmojiClickListener listener) {
            this.emojis = emojis;
            this.listener = listener;
        }

        @NonNull
        @Override
        public EmojiViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemCategoryGridCellBinding binding = ItemCategoryGridCellBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new EmojiViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull EmojiViewHolder holder, int position) {
            holder.bind(emojis.get(position), position == selectedPosition);
        }

        @Override
        public int getItemCount() {
            return emojis.size();
        }

        class EmojiViewHolder extends RecyclerView.ViewHolder {
            private final ItemCategoryGridCellBinding binding;

            public EmojiViewHolder(ItemCategoryGridCellBinding binding) {
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
                binding.txtCategoryName.setVisibility(View.GONE); // No name under emojis
                binding.txtCategoryEmoji.setText(emoji);

                int baseColor = Color.parseColor("#7C6FE0"); // Primary theme color

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
