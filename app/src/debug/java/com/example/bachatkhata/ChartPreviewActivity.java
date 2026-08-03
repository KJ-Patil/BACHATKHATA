package com.example.bachatkhata;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.data.BarEntry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Debug-only: opens {@link BarChartDetailBottomSheet} with representative data so the
 * expanded chart can be checked without a signed-in account. Not present in release.
 */
public class ChartPreviewActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        List<String> incomeLabels = Arrays.asList("Salary", "Freelance", "Investments");
        float[] incomeValues = {85000f, 22400f, 9800f};

        List<String> spentLabels = Arrays.asList(
                "Food & Dining", "Transportation", "Entertainment", "Utilities",
                "Healthcare", "Shopping", "Education", "Rent");
        float[] spentValues = {12400f, 5200f, 3100f, 4800f, 2600f, 9400f, 7200f, 25000f};

        ChartStyler.BarSeries series = ChartStyler.buildCategorySeries(
                toEntries(incomeValues), incomeLabels,
                toEntries(spentValues), spentLabels,
                "Both");

        BarChartDetailBottomSheet.newInstance("By Category", series)
                .show(getSupportFragmentManager(), "preview");
    }

    private static List<BarEntry> toEntries(float[] values) {
        List<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < values.length; i++) entries.add(new BarEntry(i, values[i]));
        return entries;
    }
}
