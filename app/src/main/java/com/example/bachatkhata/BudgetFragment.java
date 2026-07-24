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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bachatkhata.databinding.FragmentBudgetBinding;
import com.example.bachatkhata.databinding.ItemBucketRollupBinding;
import com.example.bachatkhata.databinding.ItemBucketSummaryBinding;
import com.example.bachatkhata.databinding.ItemBudgetBinding;
import com.example.bachatkhata.databinding.ItemRollupRowBinding;
import com.example.bachatkhata.domain.BucketConfig;
import com.example.bachatkhata.domain.BucketType;
import com.example.bachatkhata.domain.Categories;
import com.example.bachatkhata.domain.MoneyRule;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
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
    private final List<Transaction> monthExpenses = new ArrayList<>();
    private final List<Category> categories = new ArrayList<>();

    private static final int TAB_RULE = 0;
    private static final int TAB_CATEGORY_LIMITS = 1;
    private static final int TAB_SPENT_VS_INVESTED = 2;
    private int selectedTab = TAB_RULE;

    private final List<Transaction> monthIncome = new ArrayList<>();

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
        setupTabs();
        setupListeners();
        observeViewModel();

        if (mAuth.getCurrentUser() != null) {
            String uid = mAuth.getCurrentUser().getUid();
            viewModel.loadCategories(uid);
            // The split drives every allocation on screen, so pull the account's copy before
            // rendering rather than showing 50/30/20 and correcting it a moment later.
            MoneyRuleSettings.loadFromFirestore(requireContext(), uid, () -> {
                if (binding != null) renderRuleTab();
            });
        }

        loadData();
    }

    private void setupTabs() {
        binding.tabBudgetMode.addTab(binding.tabBudgetMode.newTab().setText("Rule Allocations"));
        binding.tabBudgetMode.addTab(binding.tabBudgetMode.newTab().setText("Category Limits"));
        binding.tabBudgetMode.addTab(binding.tabBudgetMode.newTab().setText("Spent vs Invested"));
        binding.tabBudgetMode.setTabMode(TabLayout.MODE_SCROLLABLE);

        binding.tabBudgetMode.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                selectedTab = tab.getPosition();
                applyTabVisibility();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) { }

            @Override
            public void onTabReselected(TabLayout.Tab tab) { }
        });

        applyTabVisibility();
    }

    private void applyTabVisibility() {
        if (binding == null) return;
        boolean ruleTab = selectedTab == TAB_RULE;
        boolean limitsTab = selectedTab == TAB_CATEGORY_LIMITS;
        boolean investedTab = selectedTab == TAB_SPENT_VS_INVESTED;

        binding.scrollRuleAllocations.setVisibility(ruleTab ? View.VISIBLE : View.GONE);
        binding.scrollSpentVsInvested.setVisibility(investedTab ? View.VISIBLE : View.GONE);
        binding.cardBudgetSummary.setVisibility(limitsTab ? View.VISIBLE : View.GONE);
        binding.btnAddBudget.setVisibility(limitsTab ? View.VISIBLE : View.GONE);

        if (limitsTab) {
            // Restores the correct list-vs-empty-state split for the category view.
            updateSummaryAndHealth();
        } else {
            binding.rvBudgets.setVisibility(View.GONE);
            binding.layoutEmptyState.setVisibility(View.GONE);
            if (ruleTab) renderRuleTab();
            else renderSpentVsInvestedTab();
        }
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
        binding.btnAddBudget.setOnClickListener(addListener);

        binding.btnEditIncome.setOnClickListener(v -> showEditIncomeDialog());
        binding.btnEditSplit.setOnClickListener(v -> showEditSplitDialog());
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
            renderRuleTab(); // category limits roll up into each bucket's allocation
            adapter.notifyDataSetChanged();
        });

        viewModel.getSpentPerCategory().observe(getViewLifecycleOwner(), map -> {
            spentMap.clear();
            spentMap.putAll(map);
            updateSummaryAndHealth();
            adapter.notifyDataSetChanged();
        });

        viewModel.getMonthExpenses().observe(getViewLifecycleOwner(), list -> {
            monthExpenses.clear();
            monthExpenses.addAll(list);
            renderRuleTab();
        });

        viewModel.getCategories().observe(getViewLifecycleOwner(), list -> {
            categories.clear();
            categories.addAll(list);
            renderRuleTab();
            renderSpentVsInvestedTab();
        });

        viewModel.getMonthIncome().observe(getViewLifecycleOwner(), list -> {
            monthIncome.clear();
            monthIncome.addAll(list);
            renderSpentVsInvestedTab();
        });
    }

    // ── Spent vs Invested tab ───────────────────────────────────────────

    private void renderSpentVsInvestedTab() {
        if (binding == null || selectedTab != TAB_SPENT_VS_INVESTED) return;

        CurrencyManager cm = CurrencyManager.getInstance();
        MoneyRule.Result result = MoneyRule.compute(
                MoneyRuleSettings.getIncome(requireContext()),
                monthExpenses,
                targetCalendar.get(Calendar.YEAR),
                targetCalendar.get(Calendar.MONTH),
                MoneyRuleSettings.getSplit(requireContext()),
                null,
                categories);

        binding.txtTotalConsumed.setText(cm.formatAmount(result.totalConsumed()));
        binding.txtTotalInvested.setText(cm.formatAmount(result.totalInvested()));
        binding.txtInvestRate.setText(String.format(Locale.US,
                "%.1f%% of everything that went out", result.investRate()));

        double returns = 0;
        for (Transaction t : monthIncome) {
            if (BucketConfig.isInvestmentIncome(t.getCategory())) returns += t.getAmount();
        }
        binding.txtInvestmentReturns.setText(cm.formatAmount(returns));

        // Per-bucket category rollups, largest first.
        binding.containerRollups.removeAllViews();
        for (BucketType bucket : BucketType.values()) {
            binding.containerRollups.addView(buildRollupCard(bucket));
        }
    }

    private View buildRollupCard(BucketType bucket) {
        ItemBucketRollupBinding card = ItemBucketRollupBinding.inflate(
                getLayoutInflater(), binding.containerRollups, false);
        CurrencyManager cm = CurrencyManager.getInstance();

        Map<String, Double> perCategory = new HashMap<>();
        double total = 0;
        for (Transaction t : monthExpenses) {
            if (Categories.resolveBucketForCategory(t.getCategory(), categories) != bucket) continue;
            String name = t.getCategory() != null ? t.getCategory() : "Uncategorised";
            perCategory.put(name, perCategory.getOrDefault(name, 0.0) + t.getAmount());
            total += t.getAmount();
        }

        card.txtRollupTitle.setText(bucket.label());
        card.txtRollupTotal.setText(cm.formatAmount(total));

        List<Map.Entry<String, Double>> sorted = new ArrayList<>(perCategory.entrySet());
        Collections.sort(sorted, (a, b) -> Double.compare(b.getValue(), a.getValue()));

        card.txtRollupEmpty.setVisibility(sorted.isEmpty() ? View.VISIBLE : View.GONE);
        for (Map.Entry<String, Double> entry : sorted) {
            ItemRollupRowBinding row = ItemRollupRowBinding.inflate(
                    getLayoutInflater(), card.containerRollupRows, false);
            row.txtRowCategory.setText(entry.getKey());
            row.txtRowAmount.setText(cm.formatAmount(entry.getValue()));
            card.containerRollupRows.addView(row.getRoot());
        }

        return card.getRoot();
    }

    // ── Rule Allocations tab ────────────────────────────────────────────

    private void renderRuleTab() {
        if (binding == null || selectedTab != TAB_RULE) return;

        double income = MoneyRuleSettings.getIncome(requireContext());
        MoneyRule.Split split = MoneyRuleSettings.getSplit(requireContext());

        binding.txtMonthlyIncome.setText(CurrencyManager.getInstance().formatAmount(income));
        binding.txtSplitSummary.setText(String.format(Locale.US,
                "Split %d / %d / %d  ·  Needs / Wants / Investments",
                split.needs, split.wants, split.investments));

        boolean hasIncome = income > 0;
        binding.containerBuckets.setVisibility(hasIncome ? View.VISIBLE : View.GONE);
        binding.layoutIncomeEmptyState.setVisibility(hasIncome ? View.GONE : View.VISIBLE);
        if (!hasIncome) {
            binding.containerBuckets.removeAllViews();
            return;
        }

        Map<String, Double> limits = new HashMap<>();
        for (Budget b : budgetsList) {
            limits.put(b.getCategory(), limits.getOrDefault(b.getCategory(), 0.0) + b.getLimitAmount());
        }

        MoneyRule.Result result = MoneyRule.compute(
                income,
                monthExpenses,
                targetCalendar.get(Calendar.YEAR),
                targetCalendar.get(Calendar.MONTH),
                split,
                limits,
                categories);

        binding.containerBuckets.removeAllViews();
        for (BucketType bucket : BucketType.values()) {
            binding.containerBuckets.addView(buildBucketCard(bucket, result.get(bucket)));
        }
    }

    private View buildBucketCard(BucketType bucket, MoneyRule.BucketSummary summary) {
        ItemBucketSummaryBinding card = ItemBucketSummaryBinding.inflate(
                getLayoutInflater(), binding.containerBuckets, false);

        CurrencyManager cm = CurrencyManager.getInstance();

        card.txtBucketName.setText(bucket.label());
        card.txtBucketSpentVsLimit.setText(String.format(Locale.US, "%s of %s  ·  %.1f%% used",
                cm.formatAmount(summary.spent), cm.formatAmount(summary.budget), summary.usage));
        card.progressBucket.setProgress((int) Math.min(summary.usage, 100));

        int statusColor;
        if (MoneyRule.STATUS_OVER_BUDGET.equals(summary.status)) {
            statusColor = Color.parseColor("#E24B4A");
        } else if (MoneyRule.STATUS_NEAR_LIMIT.equals(summary.status)) {
            statusColor = Color.parseColor("#EF9F27");
        } else {
            statusColor = Color.parseColor("#3DAF85");
        }
        card.txtBucketStatus.setText(summary.status);
        card.txtBucketStatus.setBackgroundTintList(ColorStateList.valueOf(statusColor));
        card.progressBucket.setIndicatorColor(statusColor);

        card.txtBucketAllocation.setText(String.format("Category limits set: %s",
                cm.formatAmount(summary.allocatedBudget)));

        // Warn when per-category limits promise more than the rule allows — the two views
        // would otherwise disagree with no explanation.
        if (summary.isOverAllocated()) {
            card.txtBucketAllocationWarning.setVisibility(View.VISIBLE);
            card.txtBucketAllocationWarning.setText(String.format(
                    "Your category limits exceed this bucket's rule limit by %s.",
                    cm.formatAmount(summary.allocatedBudget - summary.budget)));
        } else {
            card.txtBucketAllocationWarning.setVisibility(View.GONE);
        }

        return card.getRoot();
    }

    private void showEditIncomeDialog() {
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("e.g. 50000");
        double current = MoneyRuleSettings.getIncome(requireContext());
        if (current > 0) input.setText(String.format(Locale.US, "%.0f", current));

        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout container = new FrameLayout(requireContext());
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(input);

        new AlertDialog.Builder(requireContext())
                .setTitle("Monthly take-home income")
                .setMessage("Used to work out your Needs / Wants / Investments allocations.")
                .setView(container)
                .setPositiveButton("Save", (d, which) -> {
                    String raw = input.getText().toString().trim();
                    double value;
                    try {
                        value = raw.isEmpty() ? 0 : Double.parseDouble(raw);
                    } catch (NumberFormatException e) {
                        Snackbar.make(binding.getRoot(), "Enter a valid amount", Snackbar.LENGTH_SHORT).show();
                        return;
                    }
                    String uid = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
                    MoneyRuleSettings.setIncome(requireContext(), uid, value);
                    renderRuleTab();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditSplitDialog() {
        MoneyRule.Split current = MoneyRuleSettings.getSplit(requireContext());

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);

        EditText needsInput = splitField("Needs %", current.needs);
        EditText wantsInput = splitField("Wants %", current.wants);
        EditText investInput = splitField("Investments %", current.investments);
        layout.addView(needsInput);
        layout.addView(wantsInput);
        layout.addView(investInput);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Adjust your split")
                .setMessage("The three percentages must add up to exactly 100.")
                .setView(layout)
                .setPositiveButton("Save", null) // bound below so it can stay open on error
                .setNeutralButton("Reset to 50/30/20", (d, which) -> {
                    String uid = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
                    MoneyRuleSettings.setSplit(requireContext(), uid, MoneyRule.Split.defaultSplit());
                    renderRuleTab();
                })
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            MoneyRule.Split entered = new MoneyRule.Split(
                    parseIntOrZero(needsInput), parseIntOrZero(wantsInput), parseIntOrZero(investInput));

            if (!entered.isValid()) {
                Snackbar.make(binding.getRoot(),
                        "Percentages add up to " + entered.total() + "%, they must total 100%",
                        Snackbar.LENGTH_LONG).show();
                return;
            }
            String uid = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
            MoneyRuleSettings.setSplit(requireContext(), uid, entered);
            renderRuleTab();
            dialog.dismiss();
        }));

        dialog.show();
    }

    private EditText splitField(String hint, int value) {
        EditText field = new EditText(requireContext());
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setHint(hint);
        field.setText(String.valueOf(value));
        return field;
    }

    private int parseIntOrZero(EditText field) {
        try {
            String raw = field.getText().toString().trim();
            return raw.isEmpty() ? 0 : Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void updateSummaryAndHealth() {
        if (binding == null) return;
        // This owns the category-tab views; on the rule tab it must not fight applyTabVisibility.
        if (selectedTab == TAB_RULE) return;

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
