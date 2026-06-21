package com.example.bachatkhata;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

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
        // Draw on a software layer so the chart is clipped to the parent card's
        // rounded corners (hardware layers ignore the CardView corner radius and
        // leave a white squared-off corner).
        chart.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setBackgroundColor(Color.TRANSPARENT);
        chart.setTouchEnabled(true);
        chart.setPinchZoom(false);
        chart.setDoubleTapToZoomEnabled(false);
        chart.setScaleEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setDrawBorders(false);
        // Breathing room so the line never hugs the card edges (kills the "boxed" look).
        chart.setExtraOffsets(8f, 16f, 16f, 8f);
        chart.setMinOffset(0f);

        // A single data point renders as a lonely dot with an absurd auto-scaled
        // axis; only show circles when there is more than one point.
        boolean singlePoint = entries != null && entries.size() == 1;

        LineDataSet dataSet = new LineDataSet(entries, "Expense Trend");
        dataSet.setColor(Color.parseColor("#7C6FE0"));
        dataSet.setLineWidth(2.5f);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setDrawCircles(true);
        dataSet.setCircleColor(Color.WHITE);
        dataSet.setCircleHoleColor(Color.parseColor("#7C6FE0"));
        dataSet.setCircleRadius(singlePoint ? 7f : 5f);
        dataSet.setCircleHoleRadius(singlePoint ? 4f : 2.5f);
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
        xAxis.setDrawAxisLine(false);
        xAxis.setTextColor(ContextCompat.getColor(context, R.color.colorTextSecondary));
        xAxis.setTextSize(11f);
        xAxis.setGranularity(1f);
        xAxis.setAvoidFirstLastClipping(true);
        // Pad the X range so a lone point sits centred instead of pinned to the axis.
        if (singlePoint) {
            xAxis.setAxisMinimum(-0.5f);
            xAxis.setAxisMaximum(0.5f);
            xAxis.setLabelCount(1, true);
        }

        // YAxis — no gridlines/axis frame so the card doesn't read as graph paper.
        YAxis yAxisLeft = chart.getAxisLeft();
        yAxisLeft.setDrawGridLines(false);
        yAxisLeft.setDrawAxisLine(false);
        yAxisLeft.setTextColor(ContextCompat.getColor(context, R.color.colorTextSecondary));
        yAxisLeft.setTextSize(11f);
        yAxisLeft.setLabelCount(4, false);
        yAxisLeft.setSpaceTop(25f);
        // Anchor the baseline at zero so values read sensibly instead of a
        // hair-thin auto-scaled window (e.g. 998–1000) around a single point.
        yAxisLeft.setAxisMinimum(0f);
        yAxisLeft.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return formatCompact(value);
            }
        });

        YAxis yAxisRight = chart.getAxisRight();
        yAxisRight.setEnabled(false);

        chart.animateX(900, Easing.EaseInOutCubic);
        chart.invalidate();
    }

    public static void applyBarChartStyle(Context context, BarChart chart, List<BarEntry> entries, List<String> labels) {
        // Software layer so the chart respects the parent card's rounded corners.
        chart.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setBackgroundColor(Color.TRANSPARENT);
        chart.setFitBars(true);
        chart.setScaleEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setDrawBorders(false);
        chart.setExtraOffsets(8f, 16f, 16f, 8f);
        chart.setMinOffset(0f);

        BarDataSet dataSet = new BarDataSet(entries, "Categories");
        dataSet.setColors(PALETTE);
        dataSet.setValueTextSize(11f);
        dataSet.setValueTypeface(Typeface.DEFAULT_BOLD);
        dataSet.setValueTextColor(ContextCompat.getColor(context, R.color.colorTextPrimary));
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return formatCompact(value);
            }
        });

        BarData barData = new BarData(dataSet);
        // A single category at full width looks like a slab; keep bars slimmer.
        barData.setBarWidth(entries != null && entries.size() == 1 ? 0.35f : 0.55f);
        chart.setData(barData);

        // XAxis
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setGranularity(1f);
        xAxis.setTextSize(11f);
        xAxis.setTextColor(ContextCompat.getColor(context, R.color.colorTextSecondary));
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));

        // YAxis — clean: no gridlines, no frame, baseline at zero.
        YAxis yAxisLeft = chart.getAxisLeft();
        yAxisLeft.setDrawGridLines(false);
        yAxisLeft.setDrawAxisLine(false);
        yAxisLeft.setTextColor(ContextCompat.getColor(context, R.color.colorTextSecondary));
        yAxisLeft.setTextSize(11f);
        yAxisLeft.setLabelCount(4, false);
        yAxisLeft.setSpaceTop(25f);
        yAxisLeft.setAxisMinimum(0f);
        yAxisLeft.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return formatCompact(value);
            }
        });

        YAxis yAxisRight = chart.getAxisRight();
        yAxisRight.setEnabled(false);

        chart.animateY(900, Easing.EaseInOutCubic);
        chart.invalidate();
    }

    public static void applyPieChartStyle(Context context, PieChart chart, List<PieEntry> entries) {
        chart.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
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

    private static String formatCompact(float value) {
        String symbol = CurrencyManager.getInstance().getCurrentCurrencySymbol();
        if (value >= 1_00_00_000) {
            return symbol + String.format(java.util.Locale.US, "%.1fCr", value / 1_00_00_000f);
        } else if (value >= 1_00_000) {
            return symbol + String.format(java.util.Locale.US, "%.1fL", value / 1_00_000f);
        } else if (value >= 1_000) {
            return symbol + String.format(java.util.Locale.US, "%.1fK", value / 1_000f);
        } else {
            return symbol + String.format(java.util.Locale.US, "%.0f", value);
        }
    }
}
