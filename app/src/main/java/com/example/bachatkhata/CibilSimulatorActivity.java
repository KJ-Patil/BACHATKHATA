package com.example.bachatkhata;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.SeekBar;

import com.example.bachatkhata.databinding.ActivityCibilSimulatorBinding;

import java.util.Locale;

public class CibilSimulatorActivity extends BaseActivity {

    private ActivityCibilSimulatorBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCibilSimulatorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupViews();
        setupSeekBars();

        // Initial animation
        calculateScore(true);
    }

    private void setupViews() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.creditScoreGauge.setCenterLabel("CREDIT");
    }

    private void setupSeekBars() {
        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateTextValues();
                calculateScore(false);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        };

        binding.sbPaymentHistory.setOnSeekBarChangeListener(listener);
        binding.sbUtilisation.setOnSeekBarChangeListener(listener);
        binding.sbAge.setOnSeekBarChangeListener(listener);
        binding.sbMix.setOnSeekBarChangeListener(listener);
        binding.sbEnquiries.setOnSeekBarChangeListener(listener);

        updateTextValues();
    }

    private void updateTextValues() {
        binding.txtValuePaymentHistory.setText(binding.sbPaymentHistory.getProgress() + "%");
        binding.txtValueUtilisation.setText(binding.sbUtilisation.getProgress() + "%");
        
        int age = binding.sbAge.getProgress();
        binding.txtValueAge.setText(age + (age == 1 ? " Year" : " Years"));

        int mix = binding.sbMix.getProgress();
        binding.txtValueMix.setText(mix + (mix == 1 ? " Type" : " Types"));

        int enquiries = binding.sbEnquiries.getProgress();
        binding.txtValueEnquiries.setText(enquiries + (enquiries == 1 ? " Enquiry" : " Enquiries"));
    }

    private void calculateScore(boolean animate) {
        int paymentHistory = binding.sbPaymentHistory.getProgress();
        int utilisation = binding.sbUtilisation.getProgress();
        int ageYears = binding.sbAge.getProgress();
        int mixCount = binding.sbMix.getProgress();
        int enquiriesCount = binding.sbEnquiries.getProgress();

        // Weighted factor calculations (each on a scale of 0 to 100)
        double fPayment = paymentHistory;
        double fUtil = 100 - utilisation; // Lower utilisation is better
        double fAge = (ageYears / 15.0) * 100.0;
        double fMix = (mixCount / 5.0) * 100.0;
        double fEnq = (Math.max(0, 10 - enquiriesCount) / 10.0) * 100.0; // Lower enquiries are better

        // Calculate score in 300 to 900 range
        double totalWeighted = (fPayment * 0.35) + (fUtil * 0.30) + (fAge * 0.15) + (fMix * 0.10) + (fEnq * 0.10);
        int score = 300 + (int) Math.round(totalWeighted * 6.0);
        score = Math.max(300, Math.min(900, score));

        // Rating Classification
        String rating;
        int color;
        if (score >= 750) {
            rating = "Excellent (Very Low Risk)";
            color = Color.parseColor("#5DCAA5"); // Green
        } else if (score >= 650) {
            rating = "Good (Low Risk)";
            color = Color.parseColor("#5DCAA5"); // Greenish
        } else if (score >= 550) {
            rating = "Fair (Moderate Risk)";
            color = Color.parseColor("#EF9F27"); // Amber
        } else {
            rating = "Poor (High Risk)";
            color = Color.parseColor("#E24B4A"); // Red
        }

        binding.txtCreditRating.setText(rating);
        binding.txtCreditRating.setTextColor(color);

        if (animate) {
            binding.creditScoreGauge.setScore(score, 300, 900);
        } else {
            binding.creditScoreGauge.setScoreInstant(score, 300, 900);
        }
    }
}
