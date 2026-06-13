package com.example.bachatkhata;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.bachatkhata.databinding.FragmentHomeBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private HomeViewModel viewModel;
    private TransactionAdapter adapter;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;

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
    }

    private void setupRecyclerView() {
        adapter = new TransactionAdapter(transaction -> {
            // Handle clicking transaction items in list (e.g. go to detail)
            Bundle bundle = new Bundle();
            bundle.putSerializable("transaction", transaction);
            Navigation.findNavController(binding.getRoot())
                    .navigate(R.id.action_add_transaction, bundle); // fallback action or handle edit flow
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

    private void setupNavListeners() {
        binding.btnSeeAll.setOnClickListener(v -> 
            Navigation.findNavController(v).navigate(R.id.navigation_transactions)
        );

        binding.btnNotificationBell.setOnClickListener(v ->
            Navigation.findNavController(v).navigate(R.id.navigation_notifications)
        );
    }

    private void observeViewModel() {
        viewModel.getTotalIncome().observe(getViewLifecycleOwner(), income -> {
            String val = CurrencyManager.getInstance().formatAmount(income);
            binding.txtHeroIncome.setText(val);
            binding.txtKpiIncome.setText(val);
        });

        viewModel.getTotalSpent().observe(getViewLifecycleOwner(), spent -> {
            String val = CurrencyManager.getInstance().formatAmount(spent);
            binding.txtHeroSpent.setText(val);
            binding.txtKpiSpent.setText(val);
        });

        viewModel.getTotalBalance().observe(getViewLifecycleOwner(), balance -> {
            binding.txtBalanceAmount.setText(CurrencyManager.getInstance().formatAmount(balance));
        });

        viewModel.getTotalSaved().observe(getViewLifecycleOwner(), saved -> {
            binding.txtKpiSaved.setText(CurrencyManager.getInstance().formatAmount(saved));
        });

        viewModel.getRecentTransactions().observe(getViewLifecycleOwner(), list -> {
            binding.txtKpiTxnCount.setText(String.valueOf(list.size()));
            adapter.submitList(list);
        });

        viewModel.getLineChartData().observe(getViewLifecycleOwner(), entries -> {
            if (getContext() != null) {
                ChartStyler.applyLineChartStyle(getContext(), binding.lineChart, entries);
            }
        });

        viewModel.getBarChartData().observe(getViewLifecycleOwner(), entries -> {
            List<String> labels = viewModel.getBarChartLabels().getValue();
            if (getContext() != null && labels != null) {
                ChartStyler.applyBarChartStyle(getContext(), binding.barChart, entries, labels);
            }
        });
    }

    private void setGreetingText(String uid) {
        // Set Greeting based on time of day
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        String greeting = "Good morning";
        if (hour >= 12 && hour < 17) {
            greeting = "Good afternoon";
        } else if (hour >= 17) {
            greeting = "Good evening";
        }

        // Set Month/Year subtitle
        String monthYear = calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.US) + " " + calendar.get(Calendar.YEAR);
        binding.txtDateSubtitle.setText(monthYear);

        final String finalGreeting = greeting;
        mFirestore.collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String name = documentSnapshot.getString("name");
                        if (name != null && !name.trim().isEmpty()) {
                            binding.txtGreeting.setText(String.format("%s, %s", finalGreeting, name));
                        } else {
                            binding.txtGreeting.setText(String.format("%s, User", finalGreeting));
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    binding.txtGreeting.setText(String.format("%s, User", finalGreeting));
                });
    }

    private void observeNotifications(String uid) {
        mFirestore.collection("users").document(uid).collection("notifications")
                .whereEqualTo("isRead", false)
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;
                    if (value != null) {
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
