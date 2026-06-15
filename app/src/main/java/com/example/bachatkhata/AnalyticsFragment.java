package com.example.bachatkhata;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bachatkhata.databinding.FragmentAnalyticsBinding;
import com.example.bachatkhata.databinding.ItemCategoryBreakdownBinding;
import com.example.bachatkhata.databinding.ItemTopSpendingBinding;
import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AnalyticsFragment extends Fragment {

    private FragmentAnalyticsBinding binding;
    private AnalyticsViewModel viewModel;
    private FirebaseAuth mAuth;

    private LegendAdapter legendAdapter;
    private TopSpendingAdapter topSpendingAdapter;

    private final List<AnalyticsViewModel.CategoryBreakdown> breakdownList = new ArrayList<>();
    private final List<AnalyticsViewModel.CategoryBreakdown> top5List = new ArrayList<>();

    private double lastIncomeValue = 0;
    private double lastExpenseValue = 0;

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

        setupRecyclerViews();
        setupToggleGroup();
        setupPeriodTabs();
        observeViewModel();

        if (mAuth.getCurrentUser() != null) {
            viewModel.init(mAuth.getCurrentUser().getUid());
        }
    }

    private void setupRecyclerViews() {
        legendAdapter = new LegendAdapter();
        binding.rvCategoryBreakdown.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvCategoryBreakdown.setAdapter(legendAdapter);

        topSpendingAdapter = new TopSpendingAdapter();
        binding.rvTop5.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvTop5.setAdapter(topSpendingAdapter);
    }

    private void setupToggleGroup() {
        binding.btnToggleExpense.setBackgroundColor(Color.parseColor("#E24B4A"));
        binding.btnToggleExpense.setTextColor(Color.WHITE);
        binding.btnToggleIncome.setBackgroundColor(Color.TRANSPARENT);
        binding.btnToggleIncome.setTextColor(Color.parseColor("#7C6FE0"));

        binding.toggleTypeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnToggleIncome) {
                binding.btnToggleIncome.setBackgroundColor(Color.parseColor("#5DCAA5"));
                binding.btnToggleIncome.setTextColor(Color.WHITE);
                binding.btnToggleExpense.setBackgroundColor(Color.TRANSPARENT);
                binding.btnToggleExpense.setTextColor(Color.parseColor("#7C6FE0"));
                viewModel.setType("income");
            } else {
                binding.btnToggleExpense.setBackgroundColor(Color.parseColor("#E24B4A"));
                binding.btnToggleExpense.setTextColor(Color.WHITE);
                binding.btnToggleIncome.setBackgroundColor(Color.TRANSPARENT);
                binding.btnToggleIncome.setTextColor(Color.parseColor("#7C6FE0"));
                viewModel.setType("expense");
            }
        });
    }

    private void setupPeriodTabs() {
        binding.tabPeriod.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getText() != null) {
                    viewModel.setPeriod(tab.getText().toString());
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void observeViewModel() {
        // Observe KPI values with count-up animations
        viewModel.getTotalIncome().observe(getViewLifecycleOwner(), income -> {
            String symbol = CurrencyManager.getInstance().getCurrentCurrencySymbol();
            AnimationHelper.countUpAnimation(binding.txtKpiIncome, lastIncomeValue, income, 800, symbol);
            lastIncomeValue = income;
        });

        viewModel.getTotalExpense().observe(getViewLifecycleOwner(), expense -> {
            String symbol = CurrencyManager.getInstance().getCurrentCurrencySymbol();
            AnimationHelper.countUpAnimation(binding.txtKpiExpense, lastExpenseValue, expense, 800, symbol);
            lastExpenseValue = expense;
        });

        // Observe Pie Chart
        viewModel.getPieChartData().observe(getViewLifecycleOwner(), entries -> {
            if (entries.isEmpty()) {
                binding.pieChart.setVisibility(View.GONE);
                binding.txtEmptyPieChart.setVisibility(View.VISIBLE);
            } else {
                binding.pieChart.setVisibility(View.VISIBLE);
                binding.txtEmptyPieChart.setVisibility(View.GONE);
                setupPieChart(binding.pieChart, entries);
            }
        });

        // Observe Breakdown Legend & Top 5 lists
        viewModel.getCategoryBreakdowns().observe(getViewLifecycleOwner(), list -> {
            breakdownList.clear();
            breakdownList.addAll(list);
            legendAdapter.notifyDataSetChanged();

            // Populate top 5 list
            top5List.clear();
            for (int i = 0; i < Math.min(5, list.size()); i++) {
                top5List.add(list.get(i));
            }
            topSpendingAdapter.notifyDataSetChanged();
        });

        // Observe Bar Chart (Grouped comparison)
        viewModel.getBarChartLabels().observe(getViewLifecycleOwner(), labels -> {
            List<BarEntry> curr = viewModel.getBarChartCurrentData().getValue();
            List<BarEntry> prev = viewModel.getBarChartPreviousData().getValue();
            if (curr != null && prev != null && labels != null) {
                setupGroupedBarChart(binding.barChart, prev, curr, labels);
            }
        });

        // Observe Line Chart (Cash flow trend)
        viewModel.getLineChartLabels().observe(getViewLifecycleOwner(), labels -> {
            List<Entry> income = viewModel.getLineChartIncomeData().getValue();
            List<Entry> expense = viewModel.getLineChartExpenseData().getValue();
            if (income != null && expense != null && labels != null) {
                setupLineChart(binding.lineChart, income, expense, labels);
            }
        });
    }

    private void setupPieChart(PieChart chart, List<PieEntry> entries) {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setHoleRadius(60f);
        chart.setTransparentCircleRadius(65f);
        chart.setHoleColor(Color.TRANSPARENT);
        chart.setDrawEntryLabels(false);

        PieDataSet dataSet = new PieDataSet(entries, "Categories");
        int[] palette = new int[]{
                Color.parseColor("#7C6FE0"),
                Color.parseColor("#5DCAA5"),
                Color.parseColor("#EF9F27"),
                Color.parseColor("#E24B4A"),
                Color.parseColor("#F0997B"),
                Color.parseColor("#85B7EB"),
                Color.parseColor("#AFA9EC"),
                Color.parseColor("#9FE1CB")
        };
        dataSet.setColors(palette);
        dataSet.setValueTextSize(12f);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format(Locale.US, "%.1f%%", value);
            }
        });

        PieData pieData = new PieData(dataSet);
        chart.setData(pieData);
        chart.animateY(1000, Easing.EaseInOutCubic);
        chart.invalidate();
    }

    private void setupGroupedBarChart(BarChart chart, List<BarEntry> prevEntries, List<BarEntry> currEntries, List<String> labels) {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(true);
        chart.getLegend().setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        chart.getLegend().setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        chart.getLegend().setTextColor(ContextCompat.getColor(requireContext(), R.color.colorTextSecondary));

        BarDataSet prevSet = new BarDataSet(prevEntries, "Previous Month");
        prevSet.setColor(Color.parseColor("#C4BDFF")); // light purple/shadow
        prevSet.setDrawValues(false);

        BarDataSet currSet = new BarDataSet(currEntries, "Current Month");
        currSet.setColor(Color.parseColor("#7C6FE0")); // primary purple
        currSet.setDrawValues(false);

        BarData barData = new BarData(prevSet, currSet);
        float groupSpace = 0.3f;
        float barSpace = 0.05f;
        float barWidth = 0.3f; // (0.3 + 0.05) * 2 + 0.3 = 1.0

        barData.setBarWidth(barWidth);
        chart.setData(barData);
        chart.groupBars(0f, groupSpace, barSpace);

        // XAxis
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setCenterAxisLabels(true);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setTextColor(ContextCompat.getColor(requireContext(), R.color.colorTextSecondary));
        xAxis.setAxisMinimum(0f);
        xAxis.setAxisMaximum(labels.size());

        // YAxis
        YAxis yAxisLeft = chart.getAxisLeft();
        yAxisLeft.setDrawGridLines(true);
        yAxisLeft.setGridColor(Color.parseColor("#E0DEFF"));
        yAxisLeft.setTextColor(ContextCompat.getColor(requireContext(), R.color.colorTextSecondary));
        yAxisLeft.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return CurrencyManager.getInstance().formatAmount(value);
            }
        });

        chart.getAxisRight().setEnabled(false);
        chart.animateY(1000, Easing.EaseOutBounce);
        chart.invalidate();
    }

    private void setupLineChart(LineChart chart, List<Entry> incomeEntries, List<Entry> expenseEntries, List<String> labels) {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(true);
        chart.getLegend().setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        chart.getLegend().setTextColor(ContextCompat.getColor(requireContext(), R.color.colorTextSecondary));

        LineDataSet incomeSet = new LineDataSet(incomeEntries, "Income");
        incomeSet.setColor(Color.parseColor("#5DCAA5")); // colorSecondary
        incomeSet.setCircleColor(Color.parseColor("#5DCAA5"));
        incomeSet.setLineWidth(2.5f);
        incomeSet.setCircleRadius(4f);
        incomeSet.setDrawValues(false);
        incomeSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        LineDataSet expenseSet = new LineDataSet(expenseEntries, "Expense");
        expenseSet.setColor(Color.parseColor("#E24B4A")); // colorDanger
        expenseSet.setCircleColor(Color.parseColor("#E24B4A"));
        expenseSet.setLineWidth(2.5f);
        expenseSet.setCircleRadius(4f);
        expenseSet.setDrawValues(false);
        expenseSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        LineData lineData = new LineData(incomeSet, expenseSet);
        chart.setData(lineData);

        // XAxis
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setTextColor(ContextCompat.getColor(requireContext(), R.color.colorTextSecondary));
        xAxis.setGranularity(1f);

        // YAxis
        YAxis yAxisLeft = chart.getAxisLeft();
        yAxisLeft.setDrawGridLines(true);
        yAxisLeft.setGridColor(Color.parseColor("#E0DEFF"));
        yAxisLeft.setTextColor(ContextCompat.getColor(requireContext(), R.color.colorTextSecondary));
        yAxisLeft.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return CurrencyManager.getInstance().formatAmount(value);
            }
        });

        chart.getAxisRight().setEnabled(false);
        chart.animateXY(1200, 1200, Easing.EaseInOutCubic);
        chart.invalidate();
    }

    // Legend Recycler Adapter
    private class LegendAdapter extends RecyclerView.Adapter<LegendAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemCategoryBreakdownBinding b = ItemCategoryBreakdownBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(b);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(breakdownList.get(position));
        }

        @Override
        public int getItemCount() { return breakdownList.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final ItemCategoryBreakdownBinding itemBinding;

            ViewHolder(ItemCategoryBreakdownBinding itemBinding) {
                super(itemBinding.getRoot());
                this.itemBinding = itemBinding;
            }

            void bind(AnalyticsViewModel.CategoryBreakdown breakdown) {
                String type = viewModel.getSelectedType().getValue();
                int themeColor = Color.parseColor("#7C6FE0");
                if ("income".equalsIgnoreCase(type)) {
                    themeColor = Color.parseColor("#5DCAA5");
                } else if ("expense".equalsIgnoreCase(type)) {
                    themeColor = Color.parseColor("#E24B4A");
                }

                itemBinding.txtCategoryName.setText(breakdown.getCategoryName());
                itemBinding.txtCategoryPercent.setText(String.format(Locale.US, "(%.1f%%)", breakdown.getPercentage()));
                itemBinding.txtCategoryAmount.setText(CurrencyManager.getInstance().formatAmount(breakdown.getAmount()));

                itemBinding.progressWeight.setProgress((int) breakdown.getPercentage());
                itemBinding.progressWeight.setIndicatorColor(themeColor);
            }
        }
    }

    // Top Spending Ranked Recycler Adapter
    private class TopSpendingAdapter extends RecyclerView.Adapter<TopSpendingAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemTopSpendingBinding b = ItemTopSpendingBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(b);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(top5List.get(position), position + 1);
        }

        @Override
        public int getItemCount() { return top5List.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final ItemTopSpendingBinding itemBinding;

            ViewHolder(ItemTopSpendingBinding itemBinding) {
                super(itemBinding.getRoot());
                this.itemBinding = itemBinding;
            }

            void bind(AnalyticsViewModel.CategoryBreakdown breakdown, int rank) {
                itemBinding.txtRankNumber.setText(String.valueOf(rank));
                itemBinding.txtCategoryName.setText(breakdown.getCategoryName());
                itemBinding.txtCategoryPercent.setText(String.format(Locale.US, "%.1f%%", breakdown.getPercentage()));
                itemBinding.txtCategoryAmount.setText(CurrencyManager.getInstance().formatAmount(breakdown.getAmount()));

                itemBinding.progressWeight.setProgress((int) breakdown.getPercentage());
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
