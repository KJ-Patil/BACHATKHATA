package com.example.bachatkhata;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bachatkhata.databinding.FragmentBudgetBinding;
import com.example.bachatkhata.databinding.ItemBudgetBinding;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BudgetFragment extends Fragment {

    private FragmentBudgetBinding binding;
    private BudgetViewModel viewModel;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;

    private Calendar targetCalendar;
    private BudgetAdapter adapter;
    private final List<Budget> budgetsList = new ArrayList<>();
    private final Map<String, Double> spentMap = new HashMap<>();

    private final SimpleDateFormat monthYearFormat = new SimpleDateFormat("MMMM yyyy", Locale.US);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentBudgetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();
        viewModel = new ViewModelProvider(this).get(BudgetViewModel.class);

        targetCalendar = Calendar.getInstance();

        setupRecyclerView();
        setupListeners();
        observeViewModel();

        loadData();
    }

    private void setupRecyclerView() {
        adapter = new BudgetAdapter();
        binding.rvBudgets.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvBudgets.setAdapter(adapter);
    }

    private void setupListeners() {
        binding.btnPrevMonth.setOnClickListener(v -> {
            targetCalendar.add(Calendar.MONTH, -1);
            loadData();
        });

        binding.btnNextMonth.setOnClickListener(v -> {
            targetCalendar.add(Calendar.MONTH, 1);
            loadData();
        });

        View.OnClickListener addListener = v -> {
            SetBudgetBottomSheet.newInstance(
                    targetCalendar.get(Calendar.MONTH),
                    targetCalendar.get(Calendar.YEAR),
                    null,
                    this::loadData
            ).show(getChildFragmentManager(), "SET_BUDGET");
        };
        binding.fabAddBudget.setOnClickListener(addListener);
        binding.btnEmptyAddBudget.setOnClickListener(addListener);
    }

    private void loadData() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        int month = targetCalendar.get(Calendar.MONTH);
        int year = targetCalendar.get(Calendar.YEAR);

        binding.txtBudgetMonthYear.setText(monthYearFormat.format(targetCalendar.getTime()) + " Budgets");
        viewModel.loadBudgetData(uid, month, year);
    }

    private void observeViewModel() {
        viewModel.getBudgets().observe(getViewLifecycleOwner(), list -> {
            budgetsList.clear();
            budgetsList.addAll(list);
            updateSummaryAndHealth();
            adapter.notifyDataSetChanged();
        });

        viewModel.getSpentPerCategory().observe(getViewLifecycleOwner(), map -> {
            spentMap.clear();
            spentMap.putAll(map);
            updateSummaryAndHealth();
            adapter.notifyDataSetChanged();
        });
    }

    private void updateSummaryAndHealth() {
        double totalLimit = 0;
        double totalSpent = 0;

        for (Budget b : budgetsList) {
            totalLimit += b.getLimitAmount();
            totalSpent += spentMap.getOrDefault(b.getCategory(), 0.0);
        }

        binding.txtTotalBudgetAmount.setText(String.format("Total budget: %s", 
                CurrencyManager.getInstance().formatAmount(totalLimit)));

        if (totalLimit <= 0) {
            binding.txtTotalBudgetSpent.setText("Spent: ₹0.00 (0%)");
            binding.progressBudgetSummary.setProgress(0);
            binding.txtBudgetHealthBadge.setText("No Limits Set");
            binding.txtBudgetHealthBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#6B6B8A")));
            
            binding.rvBudgets.setVisibility(View.GONE);
            binding.layoutEmptyState.setVisibility(View.VISIBLE);
            return;
        }

        binding.rvBudgets.setVisibility(View.VISIBLE);
        binding.layoutEmptyState.setVisibility(View.GONE);

        double pct = (totalSpent / totalLimit) * 100;
        binding.txtTotalBudgetSpent.setText(String.format("Spent: %s (%.1f%%)", 
                CurrencyManager.getInstance().formatAmount(totalSpent), pct));
        binding.progressBudgetSummary.setProgress((int) Math.min(pct, 100));

        if (pct >= 100) {
            binding.txtBudgetHealthBadge.setText("Over Budget");
            binding.txtBudgetHealthBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E24B4A"))); // danger
        } else if (pct >= 80) {
            binding.txtBudgetHealthBadge.setText("Warning");
            binding.txtBudgetHealthBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#EF9F27"))); // warning
        } else {
            binding.txtBudgetHealthBadge.setText("On Track");
            binding.txtBudgetHealthBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#3DAF85"))); // success
        }
    }

    private void confirmDeleteBudget(Budget budget) {
        new AlertDialog.Builder(getContext())
                .setTitle("Delete Budget Limit")
                .setMessage(String.format("Are you sure you want to remove the budget limit for %s?", budget.getCategory()))
                .setPositiveButton("Remove", (dialog, which) -> {
                    if (mAuth.getCurrentUser() != null) {
                        mFirestore.collection("users").document(mAuth.getCurrentUser().getUid())
                                .collection("budgets").document(budget.getId())
                                .delete()
                                .addOnSuccessListener(aVoid -> {
                                    Snackbar.make(binding.getRoot(), "Budget deleted.", Snackbar.LENGTH_SHORT).show();
                                    loadData();
                                });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private class BudgetAdapter extends RecyclerView.Adapter<BudgetAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemBudgetBinding itemBinding = ItemBudgetBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(budgetsList.get(position));
        }

        @Override
        public int getItemCount() {
            return budgetsList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final ItemBudgetBinding itemBinding;

            public ViewHolder(ItemBudgetBinding itemBinding) {
                super(itemBinding.getRoot());
                this.itemBinding = itemBinding;

                itemView.setOnClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        SetBudgetBottomSheet.newInstance(
                                targetCalendar.get(Calendar.MONTH),
                                targetCalendar.get(Calendar.YEAR),
                                budgetsList.get(pos),
                                BudgetFragment.this::loadData
                        ).show(getChildFragmentManager(), "EDIT_BUDGET");
                    }
                });

                itemView.setOnLongClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        confirmDeleteBudget(budgetsList.get(pos));
                        return true;
                    }
                    return false;
                });
            }

            public void bind(Budget budget) {
                itemBinding.txtBudgetName.setText(budget.getCategory());
                itemBinding.txtCategoryEmoji.setText(getCategoryEmoji(budget.getCategory()));

                double spent = spentMap.getOrDefault(budget.getCategory(), 0.0);
                double limit = budget.getLimitAmount();

                itemBinding.txtBudgetSpentVsLimit.setText(String.format("%s / %s",
                        CurrencyManager.getInstance().formatAmount(spent),
                        CurrencyManager.getInstance().formatAmount(limit)));

                double pct = (spent / limit) * 100;
                itemBinding.txtBudgetPercentage.setText(String.format("%.0f%%", pct));
                itemBinding.progressBudgetCategory.setProgress((int) Math.min(pct, 100));

                // Color configuration and over-budget triggers
                int badgeColor = Color.parseColor("#3DAF85"); // green
                if (pct >= 100) {
                    badgeColor = Color.parseColor("#E24B4A"); // red
                    itemBinding.imgAlertIcon.setVisibility(View.VISIBLE);
                    itemBinding.cardBudget.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#E24B4A")));
                } else if (pct >= 80) {
                    badgeColor = Color.parseColor("#EF9F27"); // amber
                    itemBinding.imgAlertIcon.setVisibility(View.GONE);
                    itemBinding.cardBudget.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#E0DEFF")));
                } else {
                    itemBinding.imgAlertIcon.setVisibility(View.GONE);
                    itemBinding.cardBudget.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#E0DEFF")));
                }

                itemBinding.txtBudgetPercentage.setBackgroundTintList(ColorStateList.valueOf(badgeColor));
                itemBinding.progressBudgetCategory.setIndicatorColor(Color.parseColor("#FFC107")); // yellow
                itemBinding.layoutIconFrame.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#1A7C6FE0")));
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
                    default: return "📦";
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
