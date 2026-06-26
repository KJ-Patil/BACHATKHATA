package com.example.bachatkhata;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.SeekBar;

import com.example.bachatkhata.databinding.ActivityWhatIfBinding;
import com.github.mikephil.charting.data.Entry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What-If Simulator — projects the future value of a monthly SIP-style
 * contribution plus a lump sum, compounded monthly. Pure standalone math via
 * {@link WhatIfSimulator}; no transactions/Firestore involved.
 */
public class WhatIfSimulatorActivity extends BaseActivity {

    private ActivityWhatIfBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWhatIfBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { recompute(); }
            @Override public void afterTextChanged(Editable s) {}
        };
        binding.etMonthly.addTextChangedListener(watcher);
        binding.etLumpSum.addTextChangedListener(watcher);

        SeekBar.OnSeekBarChangeListener seekListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateSeekLabels();
                recompute();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };
        binding.sbYears.setOnSeekBarChangeListener(seekListener);
        binding.sbRate.setOnSeekBarChangeListener(seekListener);

        updateSeekLabels();
        recompute();
    }

    private void updateSeekLabels() {
        int years = getYears();
        binding.txtValueYears.setText(years + (years == 1 ? " Year" : " Years"));
        binding.txtValueRate.setText(getRate() + "% p.a.");
    }

    private int getYears() {
        return Math.max(1, binding.sbYears.getProgress());
    }

    private int getRate() {
        return Math.max(1, binding.sbRate.getProgress());
    }

    private double parseInput(CharSequence s) {
        try {
            String clean = s.toString().replace(",", "").trim();
            if (clean.isEmpty()) return 0.0;
            return Double.parseDouble(clean);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private void recompute() {
        double monthly = parseInput(binding.etMonthly.getText());
        double lumpSum = parseInput(binding.etLumpSum.getText());
        int years = getYears();
        int rate = getRate();

        WhatIfSimulator.Result result = WhatIfSimulator.simulate(monthly, lumpSum, years, rate);

        CurrencyManager cm = CurrencyManager.getInstance();
        binding.txtFutureValue.setText(cm.formatAmount(result.futureValue));
        binding.txtTotalInvested.setText(cm.formatAmount(result.totalInvested));
        binding.txtTotalReturns.setText(cm.formatAmount(result.totalReturns));

        renderChart(result.yearlyValues);
    }

    private void renderChart(List<Double> yearlyValues) {
        List<Entry> entries = new ArrayList<>();
        for (int year = 0; year < yearlyValues.size(); year++) {
            entries.add(new Entry(year, yearlyValues.get(year).floatValue()));
        }
        if (entries.isEmpty()) entries.add(new Entry(0, 0f));
        ChartStyler.applyLineChartStyle(this, binding.whatIfChart, entries);
    }
}
