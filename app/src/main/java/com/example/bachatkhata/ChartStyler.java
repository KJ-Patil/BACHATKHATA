package com.example.bachatkhata;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
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

import java.util.ArrayList;
import java.util.List;

public class ChartStyler {

    private static final int[] PALETTE = new int[]{
            Color.parseColor("#7C6FE0"),
            Color.parseColor("#5DCAA5"),
            Color.parseColor("#EF9F27"),
            Color.parseColor("#E24B4A"),
            Color.parseColor("#F0997B"),
            Color.parseColor("#85B7EB"),
            Color.parseColor("#AFA9EC"),
            Color.parseColor("#9FE1CB")
    };

    public static void applyLineChartStyle(Context context, LineChart chart, List<Entry> entries) {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setBackgroundColor(Color.TRANSPARENT);
        chart.setTouchEnabled(true);
        chart.setPinchZoom(false);
        chart.setDoubleTapToZoomEnabled(false);

        LineDataSet dataSet = new LineDataSet(entries, "Expense Trend");
        dataSet.setColor(Color.parseColor("#7C6FE0"));
        dataSet.setLineWidth(2.5f);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setDrawCircles(true);
        dataSet.setCircleColor(Color.WHITE);
        dataSet.setCircleHoleColor(Color.parseColor("#7C6FE0"));
        dataSet.setCircleRadius(6f);
        dataSet.setCircleHoleRadius(4f);
        dataSet.setDrawValues(false);

        // Gradient Fill
        dataSet.setDrawFilled(true);
        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#4D7C6FE0"), Color.parseColor("#007C6FE0")}
        );
        dataSet.setFillDrawable(gradient);

        // Highlight line
        dataSet.setDrawHighlightIndicators(true);
        dataSet.setHighLightColor(Color.parseColor("#7C6FE0"));
        dataSet.setDrawHorizontalHighlightIndicator(false);
        dataSet.setDrawVerticalHighlightIndicator(true);

        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);

        // XAxis
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(ContextCompat.getColor(context, R.color.colorTextSecondary));
        xAxis.setGranularity(1f);

        // YAxis
        YAxis yAxisLeft = chart.getAxisLeft();
        yAxisLeft.setDrawGridLines(true);
        yAxisLeft.setGridColor(Color.parseColor("#E0DEFF"));
        yAxisLeft.setTextColor(ContextCompat.getColor(context, R.color.colorTextSecondary));
        yAxisLeft.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return CurrencyManager.getInstance().formatAmount(value);
            }
        });

        YAxis yAxisRight = chart.getAxisRight();
        yAxisRight.setEnabled(false);

        chart.animateXY(1200, 1200, Easing.EaseInOutCubic);
        chart.invalidate();
    }

    public static void applyBarChartStyle(Context context, BarChart chart, List<BarEntry> entries, List<String> labels) {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setBackgroundColor(Color.TRANSPARENT);
        chart.setFitBars(true);

        BarDataSet dataSet = new BarDataSet(entries, "Categories");
        dataSet.setColors(PALETTE);
        dataSet.setValueTextSize(spToPx(context, 11));
        dataSet.setValueTextColor(ContextCompat.getColor(context, R.color.colorTextPrimary));
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return CurrencyManager.getInstance().formatAmount(value);
            }
        });

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.6f);
        chart.setData(barData);

        // XAxis
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(ContextCompat.getColor(context, R.color.colorTextSecondary));
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));

        // YAxis
        YAxis yAxisLeft = chart.getAxisLeft();
        yAxisLeft.setDrawGridLines(true);
        yAxisLeft.setGridColor(Color.parseColor("#E0DEFF"));
        yAxisLeft.setTextColor(ContextCompat.getColor(context, R.color.colorTextSecondary));
        yAxisLeft.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return CurrencyManager.getInstance().formatAmount(value);
            }
        });

        YAxis yAxisRight = chart.getAxisRight();
        yAxisRight.setEnabled(false);

        chart.animateY(1000, Easing.EaseOutBounce);
        chart.invalidate();
    }

    public static void applyPieChartStyle(Context context, PieChart chart, List<PieEntry> entries) {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setHoleRadius(60f);
        chart.setTransparentCircleRadius(65f);
        chart.setHoleColor(ContextCompat.getColor(context, R.color.colorBackground));
        chart.setDrawEntryLabels(false); // Show % or values only

        PieDataSet dataSet = new PieDataSet(entries, "Categories Pie");
        dataSet.setColors(PALETTE);
        dataSet.setValueTextSize(12f);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format(java.util.Locale.US, "%.1f%%", value);
            }
        });

        PieData pieData = new PieData(dataSet);
        chart.setData(pieData);

        chart.animateY(1000, Easing.EaseInOutCubic);
        chart.invalidate();
    }

    private static float spToPx(Context context, float sp) {
        return sp * context.getResources().getDisplayMetrics().scaledDensity;
    }
}
