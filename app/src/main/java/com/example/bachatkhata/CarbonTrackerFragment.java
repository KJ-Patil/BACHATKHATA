package com.example.bachatkhata;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.example.bachatkhata.databinding.FragmentCarbonTrackerBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CarbonTrackerFragment extends Fragment {

    private FragmentCarbonTrackerBinding binding;
    private FirebaseFirestore mFirestore;
    private FirebaseAuth mAuth;
    private String uid;

    private int selectedPeriodMonths = 1; // Default: This Month (1 month)

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentCarbonTrackerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mFirestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        if (mAuth.getCurrentUser() != null) {
            uid = mAuth.getCurrentUser().getUid();
        }

        binding.btnBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        setupUI();
        loadCarbonData();
    }

    private void setupUI() {
        // Chip period selector
        binding.chipGroupPeriod.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chipThisMonth) {
                selectedPeriodMonths = 1;
            } else if (checkedId == R.id.chipLastMonth) {
                selectedPeriodMonths = 2; // Will query for last month specifically
            } else if (checkedId == R.id.chipLast3Months) {
                selectedPeriodMonths = 3;
            }
            loadCarbonData();
        });
    }

    private void loadCarbonData() {
        if (uid == null) return;

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        Date startDate;
        Date endDate = cal.getTime();

        if (selectedPeriodMonths == 1) {
            // This Month (from 1st of current month)
            cal.set(Calendar.DAY_OF_MONTH, 1);
            startDate = cal.getTime();
        } else if (selectedPeriodMonths == 2) {
            // Last Month specifically
            cal.add(Calendar.MONTH, -1);
            cal.set(Calendar.DAY_OF_MONTH, 1);
            startDate = cal.getTime();

            // Set end date to last day of last month
            Calendar endCal = Calendar.getInstance();
            endCal.set(Calendar.DAY_OF_MONTH, 1);
            endCal.add(Calendar.DAY_OF_MONTH, -1);
            endCal.set(Calendar.HOUR_OF_DAY, 23);
            endCal.set(Calendar.MINUTE, 59);
            endCal.set(Calendar.SECOND, 59);
            endDate = endCal.getTime();
        } else {
            // Last 3 Months
            cal.add(Calendar.MONTH, -2);
            cal.set(Calendar.DAY_OF_MONTH, 1);
            startDate = cal.getTime();
        }

        mFirestore.collection("users").document(uid).collection("transactions")
                .whereGreaterThanOrEqualTo("date", startDate)
                .whereLessThanOrEqualTo("date", endDate)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (binding == null) return;

                    Map<String, Double> categoryTotals = new HashMap<>();
                    if (querySnapshot != null) {
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            String type = doc.getString("type");
                            String category = doc.getString("category");
                            Double amount = doc.getDouble("amount");

                            if ("expense".equalsIgnoreCase(type) && amount != null) {
                                String catName = category != null ? category : "Other";
                                categoryTotals.put(catName, categoryTotals.getOrDefault(catName, 0.0) + amount);
                            }
                        }
                    }

                    calculateEmissions(categoryTotals);
                });
    }

    private void calculateEmissions(Map<String, Double> categoryTotals) {
        Map<String, Double> categoryCO2 = new HashMap<>();
        double totalCO2 = 0.0;

        for (Map.Entry<String, Double> entry : categoryTotals.entrySet()) {
            String category = entry.getKey();
            double amount = entry.getValue();

            double coefficient = CarbonCoefficients.getCoefficient(category);
            double co2 = (amount / 1000.0) * coefficient;
            
            categoryCO2.put(category, co2);
            totalCO2 += co2;
        }

        // Bind total CO2
        binding.txtTotalCarbon.setText(String.format(Locale.US, "%.1f kg CO₂", totalCO2));

        // Format Rating Badge
        String ratingText = "Low Impact";
        int ratingColor = Color.parseColor("#00796B"); // Green

        if (totalCO2 > 150.0) {
            ratingText = "High Impact";
            ratingColor = Color.parseColor("#E24B4A"); // Red
        } else if (totalCO2 >= 50.0) {
            ratingText = "Medium Impact";
            ratingColor = Color.parseColor("#EF9F27"); // Amber
        }

        binding.txtCarbonRating.setText(ratingText);
        binding.txtCarbonRating.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ratingColor));

        // Determine Highest Category for Tips
        String highestCategory = "None";
        double highestCO2 = 0.0;
        for (Map.Entry<String, Double> entry : categoryCO2.entrySet()) {
            if (entry.getValue() > highestCO2) {
                highestCO2 = entry.getValue();
                highestCategory = entry.getKey();
            }
        }

        updateTipsCard(highestCategory);
        renderBarChart(categoryCO2);
    }

    private void updateTipsCard(String category) {
        String tip;
        switch (category) {
            case "Transport":
            case "Fuel":
                tip = "Your highest carbon category is Transport. Try carpooling, walking, cycling, or using public transit more.";
                break;
            case "Food":
                tip = "Your highest carbon category is Food. Try planning meals to avoid waste, reducing meat/dairy consumption, and choosing local produce.";
                break;
            case "Shopping":
                tip = "Your highest carbon category is Shopping. Try choosing durable, second-hand items, avoiding fast fashion, and listing needs before buying.";
                break;
            case "Bills":
                tip = "Your highest carbon category is Utilities. Switch off idle appliances, use LED bulbs, and optimize air conditioning settings.";
                break;
            case "Travel":
                tip = "Your highest carbon category is Travel. Consider rail/road alternatives for short trips, or offset flight carbon footprints.";
                break;
            case "None":
                tip = "Your carbon footprint is excellent! Continue logging your transactions to track environmental awareness.";
                break;
            default:
                tip = "Small lifestyle modifications in '" + category + "' can help reduce overall emissions. Review utility options and transit styles.";
                break;
        }
        binding.txtCarbonTip.setText(tip);
    }

    private void renderBarChart(Map<String, Double> categoryCO2) {
        BarChart chart = binding.carbonBarChart;
        chart.clear();

        if (categoryCO2.isEmpty()) {
            // Hide the whole chart card instead of leaving a large empty box.
            binding.cardCarbonChart.setVisibility(View.GONE);
            return;
        }
        binding.cardCarbonChart.setVisibility(View.VISIBLE);

        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int index = 0;

        for (Map.Entry<String, Double> entry : categoryCO2.entrySet()) {
            entries.add(new BarEntry(index, entry.getValue().floatValue()));
            labels.add(entry.getKey());
            index++;
        }

        BarDataSet dataSet = new BarDataSet(entries, "Emissions (kg CO₂)");
        dataSet.setColor(Color.parseColor("#00796B")); // Teal/Green theme
        dataSet.setValueTextColor(Color.parseColor("#1A1A2E"));
        dataSet.setValueTextSize(9f);

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.4f);

        chart.setData(barData);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);

        XAxis xAxis = chart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setGranularityEnabled(true);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(Color.parseColor("#1A1A2E"));

        chart.getAxisLeft().setDrawGridLines(true);
        chart.getAxisRight().setEnabled(false);
        chart.animateY(800);
        chart.invalidate();
    }
}
