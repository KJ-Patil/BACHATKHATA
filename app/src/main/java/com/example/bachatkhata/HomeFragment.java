package com.example.bachatkhata;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.bachatkhata.databinding.FragmentHomeBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private HomeViewModel viewModel;
    private TransactionAdapter adapter;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;

    private double lastIncome = 0.0;
    private double lastSpent = 0.0;
    private double lastSaved = 0.0;
    private int lastTxns = 0;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        setupRecyclerView();
        setupPeriodFilters();
        setupNavListeners();
        setupSwipeRefresh();
        setupSearch();
        setupKpiPopups();
        setupChartToggle();
        observeViewModel();

        if (mAuth.getCurrentUser() != null) {
            String uid = mAuth.getCurrentUser().getUid();
            
            // Initial load of dashboard
            viewModel.loadDashboardData(uid, "Monthly");
            
            // Set Greeting
            setGreetingText(uid);
            
            // Observe notification count
            observeNotifications(uid);
        }

        // Apply smooth entrance animations
        AnimationHelper.animateSlideUpIn(binding.cardBalanceHero, 500, 0);
        AnimationHelper.animateSlideUpIn(binding.chipGroupPeriod, 550, 100);
        if (binding.lineChart != null) AnimationHelper.animateSlideUpIn(binding.lineChart, 600, 200);
        if (binding.barChart != null) AnimationHelper.animateSlideUpIn(binding.barChart, 600, 300);
        AnimationHelper.animateSlideUpIn(binding.rvRecentTransactions, 650, 400);

        // Staggered card entry for KPI grid (0, 100, 200, 300ms delays)
        AnimationHelper.cardEntryAnimation(binding.cardKpiIncome, 0);
        AnimationHelper.cardEntryAnimation(binding.cardKpiSpent, 100);
        AnimationHelper.cardEntryAnimation(binding.cardKpiSaved, 200);
        AnimationHelper.cardEntryAnimation(binding.cardKpiTxnCount, 300);
    }

    private void setupRecyclerView() {
        adapter = new TransactionAdapter(transaction -> {
            Bundle bundle = new Bundle();
            bundle.putSerializable("transaction", transaction);
            Navigation.findNavController(binding.getRoot())
                    .navigate(R.id.action_add_transaction, bundle);
        });
        binding.rvRecentTransactions.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvRecentTransactions.setAdapter(adapter);
    }

    private void setupPeriodFilters() {
        binding.chipGroupPeriod.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            String period = "Monthly";
            if (id == R.id.chipDaily) {
                period = "Daily";
            } else if (id == R.id.chipQuarterly) {
                period = "Quarterly";
            } else if (id == R.id.chipYearly) {
                period = "Yearly";
            }
            viewModel.changePeriod(period);
        });
    }

    private void setupChartToggle() {
        binding.chipGroupChartMode.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            String mode = "Both";
            if (id == R.id.chipChartIncome) {
                mode = "Income";
            } else if (id == R.id.chipChartSpent) {
                mode = "Spent";
            }
            viewModel.changeChartMode(mode);
        });
    }

    private void setupNavListeners() {
        binding.btnSeeAll.setOnClickListener(v -> 
            Navigation.findNavController(v).navigate(R.id.navigation_transactions)
        );

        binding.btnNotificationBell.setOnClickListener(v ->
            Navigation.findNavController(v).navigate(R.id.navigation_notifications)
        );

        binding.btnDataTools.setOnClickListener(v ->
            Navigation.findNavController(v).navigate(R.id.action_home_to_data_tools)
        );

        if (getActivity() != null) {
            View fab = getActivity().findViewById(R.id.fabAdd);
            if (fab != null) {
                fab.setOnLongClickListener(v -> {
                    Intent intent = new Intent(getContext(), AddTransactionActivity.class);
                    intent.putExtra("startVoiceLogging", true);
                    startActivity(intent);
                    return true;
                });
            }
        }
    }

    private void setupSwipeRefresh() {
        binding.swipeRefreshLayout.setColorSchemeResources(
                R.color.colorPrimary,
                R.color.colorSecondary,
                R.color.colorAccent
        );
        binding.swipeRefreshLayout.setOnRefreshListener(() -> {
            if (mAuth.getCurrentUser() != null) {
                String uid = mAuth.getCurrentUser().getUid();
                String period = viewModel.getSelectedPeriod().getValue();
                viewModel.loadDashboardData(uid, period != null ? period : "Monthly");
                setGreetingText(uid);
            } else {
                binding.swipeRefreshLayout.setRefreshing(false);
            }
        });
    }


    private void setupSearch() {
        binding.etSearchHome.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (binding == null) return;
                String query = s.toString();
                android.util.Log.d("HomeSearch", "HomeFragment: onTextChanged: query = '" + query + "'");
                viewModel.setSearchQuery(query);
                if (query.trim().isEmpty()) {
                    binding.txtRecentTitle.setText("Recent");
                    binding.layoutDashboardWidgets.setVisibility(View.VISIBLE);
                } else {
                    binding.txtRecentTitle.setText("Search Results");
                    binding.layoutDashboardWidgets.setVisibility(View.GONE);
                }
                binding.nestedScrollView.post(() -> {
                    if (binding != null) {
                        binding.nestedScrollView.scrollTo(0, 0);
                    }
                });
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupKpiPopups() {
        binding.cardKpiIncome.setOnClickListener(v -> showKpiPopup(
                "Total Income",
                CurrencyManager.getInstance().formatAmount(lastIncome),
                R.color.colorSecondary));

        binding.cardKpiSpent.setOnClickListener(v -> showKpiPopup(
                "Total Spent",
                CurrencyManager.getInstance().formatAmount(lastSpent),
                R.color.colorDanger));

        binding.cardKpiSaved.setOnClickListener(v -> showKpiPopup(
                "Total Saved",
                CurrencyManager.getInstance().formatAmount(lastSaved),
                R.color.colorPrimary));

        binding.cardKpiTxnCount.setOnClickListener(v -> showKpiPopup(
                "Transactions",
                String.valueOf(lastTxns),
                R.color.colorAccent));
    }

    private void showKpiPopup(String label, String value, int accentColorRes) {
        if (getContext() == null) return;

        View content = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_kpi_detail, null, false);

        content.findViewById(R.id.kpiDetailDot)
                .setBackgroundTintList(ContextCompat.getColorStateList(getContext(), accentColorRes));
        ((TextView) content.findViewById(R.id.txtKpiDetailValue)).setText(value);
        ((TextView) content.findViewById(R.id.txtKpiDetailLabel)).setText(label);

        String period = viewModel.getSelectedPeriod().getValue();
        TextView periodView = content.findViewById(R.id.txtKpiDetailPeriod);
        if (period != null && !period.isEmpty()) {
            periodView.setText(period);
            periodView.setVisibility(View.VISIBLE);
        } else {
            periodView.setVisibility(View.GONE);
        }

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(content)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();
    }

    private void observeViewModel() {
        viewModel.getTotalIncome().observe(getViewLifecycleOwner(), income -> {
            String val = CurrencyManager.getInstance().formatAmount(income);
            binding.txtHeroIncome.setText(val);
            AnimationHelper.countUpAmountAnimation(binding.txtKpiIncome, lastIncome, income, 1000);
            lastIncome = income;
        });

        viewModel.getTotalSpent().observe(getViewLifecycleOwner(), spent -> {
            String val = CurrencyManager.getInstance().formatAmount(spent);
            binding.txtHeroSpent.setText(val);
            AnimationHelper.countUpAmountAnimation(binding.txtKpiSpent, lastSpent, spent, 1000);
            lastSpent = spent;
        });

        viewModel.getTotalBalance().observe(getViewLifecycleOwner(), balance -> {
            binding.txtBalanceAmount.setText(CurrencyManager.getInstance().formatAmount(balance));
        });

        viewModel.getTotalSaved().observe(getViewLifecycleOwner(), saved -> {
            AnimationHelper.countUpAmountAnimation(binding.txtKpiSaved, lastSaved, saved, 1000);
            lastSaved = saved;
        });

        viewModel.getRecentTransactions().observe(getViewLifecycleOwner(), list -> {
            int txnCount = list.size();
            AnimationHelper.countUpIntegerAnimation(binding.txtKpiTxnCount, lastTxns, txnCount, 1000);
            lastTxns = txnCount;
            adapter.submitList(list);
            binding.swipeRefreshLayout.setRefreshing(false);
        });

        viewModel.getTodaySpent().observe(getViewLifecycleOwner(), todaySpent -> {
            String val = CurrencyManager.getInstance().formatAmount(todaySpent);
            binding.txtTodaySummaryText.setText("Spent " + val + " today");
        });

        viewModel.getSafeToSpend().observe(getViewLifecycleOwner(), result -> {
            if (result == null || binding == null) return;
            CurrencyManager cm = CurrencyManager.getInstance();
            binding.txtSafeToSpendAmount.setText(cm.formatAmount(result.safePerDay));
            String dayLabel = result.daysLeft == 1 ? "day" : "days";
            binding.txtSafeToSpendSubtitle.setText(
                    cm.formatAmount(result.poolRemaining) + " left · " + result.daysLeft + " " + dayLabel);
        });

        viewModel.getLineIncomeData().observe(getViewLifecycleOwner(), entries -> renderLineChart());
        viewModel.getLineSpentData().observe(getViewLifecycleOwner(), entries -> renderLineChart());
        viewModel.getBarIncomeData().observe(getViewLifecycleOwner(), entries -> renderBarChart());
        viewModel.getBarSpentData().observe(getViewLifecycleOwner(), entries -> renderBarChart());
        viewModel.getChartMode().observe(getViewLifecycleOwner(), mode -> {
            renderLineChart();
            renderBarChart();
        });
    }

    private void renderLineChart() {
        if (getContext() == null || binding == null) return;
        List<com.github.mikephil.charting.data.Entry> income = viewModel.getLineIncomeData().getValue();
        List<com.github.mikephil.charting.data.Entry> spent = viewModel.getLineSpentData().getValue();
        if (income == null || spent == null) return;
        ChartStyler.applyLineChartStyle(getContext(), binding.lineChart, income, spent,
                viewModel.getChartMode().getValue());
    }

    private void renderBarChart() {
        if (getContext() == null || binding == null) return;
        List<com.github.mikephil.charting.data.BarEntry> income = viewModel.getBarIncomeData().getValue();
        List<String> incomeLabels = viewModel.getBarIncomeLabels().getValue();
        List<com.github.mikephil.charting.data.BarEntry> spent = viewModel.getBarSpentData().getValue();
        List<String> spentLabels = viewModel.getBarSpentLabels().getValue();
        if (income == null || incomeLabels == null || spent == null || spentLabels == null) return;
        ChartStyler.applyBarChartStyle(getContext(), binding.barChart, income, incomeLabels,
                spent, spentLabels, viewModel.getChartMode().getValue());
    }

    private void setGreetingText(String uid) {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        String greeting = "Good morning";
        if (hour >= 12 && hour < 17) {
            greeting = "Good afternoon";
        } else if (hour >= 17) {
            greeting = "Good evening";
        }

        String monthYear = calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.US) + " " + calendar.get(Calendar.YEAR);
        binding.txtDateSubtitle.setText(monthYear);

        final String finalGreeting = greeting;
        mFirestore.collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists() && binding != null && getContext() != null) {
                        String name = documentSnapshot.getString("name");
                        if (name != null && !name.trim().isEmpty()) {
                            binding.txtGreeting.setText(String.format("%s, %s", finalGreeting, name));
                        } else {
                            binding.txtGreeting.setText(String.format("%s, User", finalGreeting));
                        }

                        // Load and display Weekly Insight
                        String weeklyInsight = documentSnapshot.getString("weeklyInsight");
                        if (weeklyInsight != null && !weeklyInsight.trim().isEmpty()) {
                            android.content.SharedPreferences prefs = getContext().getSharedPreferences("BachatKhataPrefs", Context.MODE_PRIVATE);
                            String lastDismissed = prefs.getString("lastDismissedInsight", "");
                            if (!weeklyInsight.equals(lastDismissed)) {
                                binding.cardWeeklyInsight.setVisibility(View.VISIBLE);
                                binding.txtWeeklyInsightContent.setText(weeklyInsight);
                                binding.btnDismissInsight.setOnClickListener(v -> {
                                    binding.cardWeeklyInsight.setVisibility(View.GONE);
                                    prefs.edit().putString("lastDismissedInsight", weeklyInsight).apply();
                                });
                            } else {
                                binding.cardWeeklyInsight.setVisibility(View.GONE);
                            }
                        } else {
                            binding.cardWeeklyInsight.setVisibility(View.GONE);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (binding != null) {
                        binding.txtGreeting.setText(String.format("%s, User", finalGreeting));
                    }
                });
    }

    private void observeNotifications(String uid) {
        mFirestore.collection("users").document(uid).collection("notifications")
                .whereEqualTo("isRead", false)
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;
                    if (value != null && binding != null) {
                        int count = value.size();
                        if (count > 0) {
                            binding.txtNotificationBadge.setText(String.valueOf(count));
                            binding.txtNotificationBadge.setVisibility(View.VISIBLE);
                        } else {
                            binding.txtNotificationBadge.setVisibility(View.GONE);
                        }
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
