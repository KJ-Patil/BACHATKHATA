package com.example.bachatkhata;

import android.app.AlertDialog;
import android.content.Intent;
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
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bachatkhata.databinding.FragmentSavingsBinding;
import com.example.bachatkhata.databinding.ItemSavingsGoalBinding;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SavingsFragment extends Fragment {

    private FragmentSavingsBinding binding;
    private SavingsViewModel viewModel;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;

    private SavingsAdapter adapter;
    private final List<SavingsGoal> goalsList = new ArrayList<>();
    private final SimpleDateFormat deadlineFormat = new SimpleDateFormat("MMM yyyy", Locale.US);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSavingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();
        viewModel = new ViewModelProvider(this).get(SavingsViewModel.class);

        setupRecyclerView();
        setupListeners();
        observeViewModel();

        if (mAuth.getCurrentUser() != null) {
            viewModel.loadSavingsGoals(mAuth.getCurrentUser().getUid());
        }
    }

    private void setupRecyclerView() {
        adapter = new SavingsAdapter();
        binding.rvSavingsGoals.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvSavingsGoals.setAdapter(adapter);
    }

    private void setupListeners() {
        View.OnClickListener addListener = v ->
                startActivity(new Intent(getContext(), AddSavingsGoalActivity.class));
        
        binding.fabAddSavingsGoal.setOnClickListener(addListener);
        binding.btnEmptyAddSavingsGoal.setOnClickListener(addListener);
    }

    private void observeViewModel() {
        viewModel.getSavingsGoals().observe(getViewLifecycleOwner(), list -> {
            goalsList.clear();
            goalsList.addAll(list);

            if (list.isEmpty()) {
                binding.layoutEmptyState.setVisibility(View.VISIBLE);
                binding.rvSavingsGoals.setVisibility(View.GONE);
                binding.txtTotalSavedAmount.setText("₹0.00");
                binding.txtTotalSavedSubtitle.setText("Across 0 active goals");
            } else {
                binding.layoutEmptyState.setVisibility(View.GONE);
                binding.rvSavingsGoals.setVisibility(View.VISIBLE);

                double totalSaved = 0;
                int activeGoalsCount = 0;
                for (SavingsGoal g : list) {
                    totalSaved += g.getSavedAmount();
                    if (g.getSavedAmount() < g.getTargetAmount()) {
                        activeGoalsCount++;
                    }
                }
                binding.txtTotalSavedAmount.setText(CurrencyManager.getInstance().formatAmount(totalSaved));
                binding.txtTotalSavedSubtitle.setText(String.format("Across %d active goals", activeGoalsCount));
            }

            adapter.notifyDataSetChanged();
        });
    }

    private void showAddFundsDialog(SavingsGoal goal) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(String.format("Add Funds to %s", goal.getName()));

        // Programmatically create input layout for dialog
        FrameLayout container = new FrameLayout(getContext());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = params.rightMargin = params.topMargin = params.bottomMargin = 48; // padding
        
        EditText input = new EditText(getContext());
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
                    addFundsToGoal(goal, amount);
                }
            } catch (Exception ignored) {}
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void addFundsToGoal(SavingsGoal goal, double amount) {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        double updatedAmount = goal.getSavedAmount() + amount;

        // Log transaction history
        Map<String, Object> transaction = new HashMap<>();
        transaction.put("amount", amount);
        transaction.put("date", Timestamp.now());
        transaction.put("type", "deposit");

        mFirestore.collection("users").document(uid)
                .collection("savings_goals").document(goal.getId())
                .update("savedAmount", updatedAmount)
                .addOnSuccessListener(aVoid -> {
                    // Log in subcollection
                    mFirestore.collection("users").document(uid)
                            .collection("savings_goals").document(goal.getId())
                            .collection("transactions").add(transaction);

                    // Add local notification
                    saveNotificationToFirestore(
                            "Funds Added",
                            String.format("Added %s to savings goal %s",
                                    CurrencyManager.getInstance().formatAmount(amount), goal.getName())
                    );

                    Snackbar.make(binding.getRoot(), "Savings progress updated!", Snackbar.LENGTH_SHORT).show();
                    if (mAuth.getCurrentUser() != null) {
                        viewModel.loadSavingsGoals(uid);
                    }
                })
                .addOnFailureListener(e -> {
                    Snackbar.make(binding.getRoot(), "Failed to update savings: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
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

    private class SavingsAdapter extends RecyclerView.Adapter<SavingsAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemSavingsGoalBinding itemBinding = ItemSavingsGoalBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(goalsList.get(position));
        }

        @Override
        public int getItemCount() {
            return goalsList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final ItemSavingsGoalBinding itemBinding;

            public ViewHolder(ItemSavingsGoalBinding itemBinding) {
                super(itemBinding.getRoot());
                this.itemBinding = itemBinding;

                itemView.setOnClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        Intent intent = new Intent(getContext(), SavingsDetailActivity.class);
                        intent.putExtra("savings_goal", goalsList.get(pos));
                        startActivity(intent);
                    }
                });

                itemBinding.btnAddFunds.setOnClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        showAddFundsDialog(goalsList.get(pos));
                    }
                });
            }

            public void bind(SavingsGoal goal) {
                itemBinding.txtGoalIcon.setText(goal.getIcon() != null ? goal.getIcon() : "💰");
                itemBinding.txtGoalName.setText(goal.getName());

                double saved = goal.getSavedAmount();
                double target = goal.getTargetAmount();
                double remaining = target - saved;

                itemBinding.txtGoalSavedVsTarget.setText(String.format("Saved: %s / %s",
                        CurrencyManager.getInstance().formatAmount(saved),
                        CurrencyManager.getInstance().formatAmount(target)));

                itemBinding.txtGoalRemaining.setText(String.format("Remaining: %s",
                        CurrencyManager.getInstance().formatAmount(Math.max(0, remaining))));

                double pct = (saved / target) * 100;
                itemBinding.progressRing.setProgress((float) Math.min(pct, 100));

                if (goal.getDeadline() != null) {
                    Date deadlineDate = goal.getDeadline().toDate();
                    itemBinding.txtGoalDeadline.setText(deadlineFormat.format(deadlineDate));

                    // Calculate monthly recommended savings
                    long diffMs = deadlineDate.getTime() - System.currentTimeMillis();
                    double months = diffMs / (1000.0 * 60 * 60 * 24 * 30.44);
                    if (months <= 0) months = 1;

                    if (remaining > 0) {
                        double needed = remaining / months;
                        itemBinding.txtGoalMonthlyNeeded.setText(String.format("%s/month needed to reach goal",
                                CurrencyManager.getInstance().formatAmount(needed)));
                        itemBinding.txtGoalMonthlyNeeded.setVisibility(View.VISIBLE);
                    } else {
                        itemBinding.txtGoalMonthlyNeeded.setVisibility(View.GONE);
                    }
                } else {
                    itemBinding.txtGoalDeadline.setText("No Deadline");
                    itemBinding.txtGoalMonthlyNeeded.setVisibility(View.GONE);
                }

                if (saved >= target) {
                    itemBinding.btnAddFunds.setVisibility(View.GONE);
                    itemBinding.txtGoalDeadline.setText("Completed!");
                    itemBinding.txtGoalDeadline.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#3DAF85"))); // green
                    itemBinding.txtGoalDeadline.setTextColor(Color.WHITE);
                } else {
                    itemBinding.btnAddFunds.setVisibility(View.VISIBLE);
                    itemBinding.txtGoalDeadline.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E0DEFF")));
                    itemBinding.txtGoalDeadline.setTextColor(Color.parseColor("#6B6B8A"));
                }
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
