package com.example.bachatkhata;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.example.bachatkhata.databinding.ActivityMoodInsightBinding;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MoodInsightActivity extends BaseActivity {

    private ActivityMoodInsightBinding binding;
    private FirebaseFirestore mFirestore;
    private FirebaseAuth mAuth;
    private String uid;
    private String weekKey;
    private Date weekStartDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMoodInsightBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mFirestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        if (mAuth.getCurrentUser() == null) {
            finish();
            return;
        }

        uid = mAuth.getCurrentUser().getUid();

        calculateCurrentWeekRange();
        setupUI();
        loadCurrentWeekMood();
        loadSettings();
        loadMoodLogs();
    }

    private void calculateCurrentWeekRange() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int daysToSubtract = (dayOfWeek - Calendar.MONDAY + 7) % 7;
        cal.add(Calendar.DAY_OF_YEAR, -daysToSubtract);
        weekStartDate = cal.getTime();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        weekKey = sdf.format(weekStartDate);
    }

    private void setupUI() {
        binding.btnBack.setOnClickListener(v -> finish());

        // Quick check-in buttons inside the app
        binding.btnMoodGood.setOnClickListener(v -> saveMood("Good"));
        binding.btnMoodOkay.setOnClickListener(v -> saveMood("Okay"));
        binding.btnMoodStressed.setOnClickListener(v -> saveMood("Stressed"));

        // Switch reminder preference
        binding.switchMoodReminder.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mFirestore.collection("users").document(uid)
                    .update("moodEnabled", isChecked)
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to save settings", Toast.LENGTH_SHORT).show());
        });
    }

    private void loadCurrentWeekMood() {
        mFirestore.collection("users").document(uid)
                .collection("mood_logs").document(weekKey)
                .get()
                .addOnSuccessListener(doc -> {
                    if (binding == null) return;
                    if (doc.exists()) {
                        String mood = doc.getString("mood");
                        showLoggedState(mood);
                    } else {
                        showUnloggedState();
                    }
                });
    }

    private void showLoggedState(String mood) {
        binding.layoutUnloggedMood.setVisibility(View.GONE);
        binding.layoutLoggedMood.setVisibility(View.VISIBLE);
        binding.txtMoodBadge.setText(mood);

        int color = Color.parseColor("#7C6FE0"); // Okay
        if ("Good".equalsIgnoreCase(mood)) {
            color = Color.parseColor("#1D9E75"); // Good
        } else if ("Stressed".equalsIgnoreCase(mood)) {
            color = Color.parseColor("#E24B4A"); // Stressed
        }
        binding.txtMoodBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
    }

    private void showUnloggedState() {
        binding.layoutLoggedMood.setVisibility(View.GONE);
        binding.layoutUnloggedMood.setVisibility(View.VISIBLE);
    }

    private void loadSettings() {
        mFirestore.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (binding == null) return;
                    if (doc.exists() && doc.contains("moodEnabled")) {
                        Boolean enabled = doc.getBoolean("moodEnabled");
                        if (enabled != null) {
                            binding.switchMoodReminder.setOnCheckedChangeListener(null);
                            binding.switchMoodReminder.setChecked(enabled);
                            binding.switchMoodReminder.setOnCheckedChangeListener((buttonView, isChecked) -> {
                                mFirestore.collection("users").document(uid).update("moodEnabled", isChecked);
                            });
                        }
                    } else {
                        binding.switchMoodReminder.setChecked(true); // default true
                    }
                });
    }

    private void saveMood(String mood) {
        showLoadingDialog();

        mFirestore.collection("users").document(uid).collection("transactions")
                .whereGreaterThanOrEqualTo("date", weekStartDate)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    double totalSpend = 0.0;
                    if (querySnapshot != null) {
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            String type = doc.getString("type");
                            Double amount = doc.getDouble("amount");
                            if ("expense".equalsIgnoreCase(type) && amount != null) {
                                totalSpend += amount;
                            }
                        }
                    }

                    double avgSpend = totalSpend / 7.0;

                    Map<String, Object> log = new HashMap<>();
                    log.put("mood", mood);
                    log.put("weekStartDate", weekStartDate);
                    log.put("totalSpendThatWeek", totalSpend);
                    log.put("avgSpendThatWeek", avgSpend);
                    log.put("timestamp", Timestamp.now());

                    mFirestore.collection("users").document(uid)
                            .collection("mood_logs").document(weekKey)
                            .set(log)
                            .addOnSuccessListener(aVoid -> {
                                hideLoadingDialog();
                                showLoggedState(mood);
                                showSnackbar("Mood checked in for this week!", "SUCCESS");
                                loadMoodLogs(); // reload chart and correlation
                            })
                            .addOnFailureListener(e -> {
                                hideLoadingDialog();
                                showSnackbar("Failed to log mood", "ERROR");
                            });
                })
                .addOnFailureListener(e -> {
                    hideLoadingDialog();
                    showSnackbar("Failed to query transactions", "ERROR");
                });
    }

    private void loadMoodLogs() {
        mFirestore.collection("users").document(uid)
                .collection("mood_logs")
                .orderBy("weekStartDate", Query.Direction.DESCENDING)
                .limit(12)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (binding == null) return;
                    if (querySnapshot == null || querySnapshot.isEmpty()) {
                        binding.txtCorrelationResult.setText("Check in weekly to see patterns emerge after 4 weeks");
                        setupBarChart(new ArrayList<>());
                        return;
                    }

                    List<DocumentSnapshot> logs = new ArrayList<>(querySnapshot.getDocuments());
                    // Reverse to show chronological order (left-to-right)
                    Collections.reverse(logs);

                    setupBarChart(logs);
                    calculateCorrelation(logs);
                });
    }

    private void setupBarChart(List<DocumentSnapshot> logs) {
        BarChart chart = binding.moodBarChart;
        chart.clear();

        if (logs.isEmpty()) {
            chart.setNoDataText("No logs found. Check in weekly to view graph.");
            chart.invalidate();
            return;
        }

        List<BarEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        SimpleDateFormat weekFormat = new SimpleDateFormat("dd/MM", Locale.US);

        for (int i = 0; i < logs.size(); i++) {
            DocumentSnapshot doc = logs.get(i);
            String mood = doc.getString("mood");
            Double spend = doc.getDouble("totalSpendThatWeek");
            Date start = doc.getDate("weekStartDate");

            double val = spend != null ? spend : 0.0;
            entries.add(new BarEntry(i, (float) val));

            String label = start != null ? weekFormat.format(start) : "";
            labels.add(label);

            int color = Color.parseColor("#7C6FE0"); // Okay
            if ("Good".equalsIgnoreCase(mood)) {
                color = Color.parseColor("#1D9E75"); // Good
            } else if ("Stressed".equalsIgnoreCase(mood)) {
                color = Color.parseColor("#E24B4A"); // Stressed
            }
            colors.add(color);
        }

        BarDataSet dataSet = new BarDataSet(entries, "Weekly Spend (Color shows Mood)");
        dataSet.setColors(colors);
        dataSet.setValueTextColor(Color.parseColor("#1A1A2E"));
        dataSet.setValueTextSize(9f);

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.5f);

        chart.setData(barData);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(true);

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

    private void calculateCorrelation(List<DocumentSnapshot> logs) {
        if (logs.size() < 4) {
            binding.txtCorrelationResult.setText("Check in weekly to see patterns emerge after 4 weeks (Currently: " + logs.size() + " logs)");
            return;
        }

        double stressedSum = 0.0;
        int stressedCount = 0;

        double normalSum = 0.0;
        int normalCount = 0;

        for (DocumentSnapshot doc : logs) {
            String mood = doc.getString("mood");
            Double spend = doc.getDouble("totalSpendThatWeek");
            if (spend == null) continue;

            if ("Stressed".equalsIgnoreCase(mood)) {
                stressedSum += spend;
                stressedCount++;
            } else {
                normalSum += spend;
                normalCount++;
            }
        }

        if (stressedCount > 0 && normalCount > 0) {
            double avgStressed = stressedSum / stressedCount;
            double avgNormal = normalSum / normalCount;

            if (avgStressed > avgNormal) {
                double diff = avgStressed - avgNormal;
                binding.txtCorrelationResult.setText(String.format(Locale.US,
                        "When you feel stressed, you spend an average of ₹%.2f more than usual.", diff));
            } else {
                binding.txtCorrelationResult.setText("No significant increase in spending detected during stressed weeks. Good job maintaining control!");
            }
        } else if (stressedCount == 0) {
            binding.txtCorrelationResult.setText("No stressed weeks logged yet. Keep tracking to see if correlations occur.");
        } else {
            binding.txtCorrelationResult.setText("Not enough variation. Try to record your mood honestly each week.");
        }
    }
}
