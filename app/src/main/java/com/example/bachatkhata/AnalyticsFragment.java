package com.example.bachatkhata;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bachatkhata.databinding.FragmentAnalyticsBinding;
import com.example.bachatkhata.databinding.ItemCategoryBreakdownBinding;
import com.github.mikephil.charting.data.PieEntry;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AnalyticsFragment extends Fragment {

    private FragmentAnalyticsBinding binding;
    private AnalyticsViewModel viewModel;
    private FirebaseAuth mAuth;

    private BreakdownAdapter adapter;
    private final List<AnalyticsViewModel.CategoryBreakdown> breakdownList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAnalyticsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        viewModel = new ViewModelProvider(this).get(AnalyticsViewModel.class);

        setupRecyclerView();
        setupToggleGroup();
        observeViewModel();

        if (mAuth.getCurrentUser() != null) {
            viewModel.init(mAuth.getCurrentUser().getUid());
        }
    }

    private void setupRecyclerView() {
        adapter = new BreakdownAdapter();
        binding.rvCategoryBreakdown.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvCategoryBreakdown.setAdapter(adapter);
    }

    private void setupToggleGroup() {
        // Default color for Expense: Red
        binding.btnToggleExpense.setBackgroundColor(Color.parseColor("#E24B4A"));
        binding.btnToggleExpense.setTextColor(Color.WHITE);
        binding.btnToggleIncome.setBackgroundColor(Color.TRANSPARENT);
        binding.btnToggleIncome.setTextColor(Color.parseColor("#7C6FE0"));

        binding.toggleTypeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnToggleIncome) {
                // Selected Income: Green
                binding.btnToggleIncome.setBackgroundColor(Color.parseColor("#5DCAA5"));
                binding.btnToggleIncome.setTextColor(Color.WHITE);
                binding.btnToggleExpense.setBackgroundColor(Color.TRANSPARENT);
                binding.btnToggleExpense.setTextColor(Color.parseColor("#7C6FE0"));
                viewModel.setType("income");
            } else {
                // Selected Expense: Red
                binding.btnToggleExpense.setBackgroundColor(Color.parseColor("#E24B4A"));
                binding.btnToggleExpense.setTextColor(Color.WHITE);
                binding.btnToggleIncome.setBackgroundColor(Color.TRANSPARENT);
                binding.btnToggleIncome.setTextColor(Color.parseColor("#7C6FE0"));
                viewModel.setType("expense");
            }
        });
    }

    private void observeViewModel() {
        // Observe Category PieChart Data
        viewModel.getPieChartData().observe(getViewLifecycleOwner(), entries -> {
            if (entries.isEmpty()) {
                binding.pieChart.setVisibility(View.GONE);
                binding.txtEmptyPieChart.setVisibility(View.VISIBLE);
            } else {
                binding.pieChart.setVisibility(View.VISIBLE);
                binding.txtEmptyPieChart.setVisibility(View.GONE);
                if (getContext() != null) {
                    ChartStyler.applyPieChartStyle(getContext(), binding.pieChart, entries);
                }
            }
        });

        // Observe Category Detail Breakdown rows
        viewModel.getCategoryBreakdowns().observe(getViewLifecycleOwner(), list -> {
            breakdownList.clear();
            breakdownList.addAll(list);
            if (breakdownList.isEmpty()) {
                binding.cardCategoryList.setVisibility(View.GONE);
            } else {
                binding.cardCategoryList.setVisibility(View.VISIBLE);
            }
            adapter.notifyDataSetChanged();
        });

        // Observe Monthly Savings BarChart Data
        viewModel.getBarChartData().observe(getViewLifecycleOwner(), entries -> {
            List<String> labels = viewModel.getBarChartLabels().getValue();
            if (getContext() != null && labels != null) {
                ChartStyler.applyBarChartStyle(getContext(), binding.barChart, entries, labels);
            }
        });
    }

    private class BreakdownAdapter extends RecyclerView.Adapter<BreakdownAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemCategoryBreakdownBinding itemBinding = ItemCategoryBreakdownBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(breakdownList.get(position));
        }

        @Override
        public int getItemCount() {
            return breakdownList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final ItemCategoryBreakdownBinding itemBinding;

            public ViewHolder(ItemCategoryBreakdownBinding itemBinding) {
                super(itemBinding.getRoot());
                this.itemBinding = itemBinding;
            }

            public void bind(AnalyticsViewModel.CategoryBreakdown breakdown) {
                // Check if user selected income or expense to style progress indicators
                String type = viewModel.getSelectedType().getValue();
                int themeColor = Color.parseColor("#7C6FE0"); // Primary
                if ("income".equalsIgnoreCase(type)) {
                    themeColor = Color.parseColor("#5DCAA5"); // Green
                } else if ("expense".equalsIgnoreCase(type)) {
                    themeColor = Color.parseColor("#E24B4A"); // Red
                }

                itemBinding.txtCategoryName.setText(breakdown.getCategoryName());
                itemBinding.txtCategoryPercent.setText(String.format(Locale.US, "(%.1f%%)", breakdown.getPercentage()));
                itemBinding.txtCategoryAmount.setText(CurrencyManager.getInstance().formatAmount(breakdown.getAmount()));

                itemBinding.progressWeight.setProgress((int) breakdown.getPercentage());
                itemBinding.progressWeight.setIndicatorColor(themeColor);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
