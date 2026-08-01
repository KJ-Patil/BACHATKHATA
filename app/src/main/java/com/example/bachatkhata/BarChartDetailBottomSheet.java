package com.example.bachatkhata;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bachatkhata.databinding.ItemChartCategoryBinding;
import com.example.bachatkhata.databinding.SheetBarChartDetailBinding;
import com.github.mikephil.charting.data.BarEntry;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Expanded view of a bar chart. The inline card can only fit a handful of bars
 * before the labels collide, so tapping it opens this sheet, which caps the
 * viewport at {@link #VISIBLE_BARS} bars and lets the user drag sideways to
 * reach the rest of the series.
 */
public class BarChartDetailBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_TITLE = "title";
    private static final String ARG_LABELS = "labels";
    private static final String ARG_VALUES = "values";
    private static final String ARG_COLORS = "colors";
    private static final String ARG_INCOME = "income";

    /** How many bars stay on screen at once; the rest are one drag away. */
    private static final int VISIBLE_BARS = 6;

    /** Floor for the scrollbar thumb so it stays grabbable on a long series. */
    private static final float MIN_THUMB_WIDTH_DP = 40f;

    private SheetBarChartDetailBinding binding;
    private ViewTreeObserver.OnPreDrawListener preDrawSync;

    private ChartStyler.BarSeries series;
    private CategoryAdapter adapter;
    /** Indices into {@link #series}, narrowed by the search box. */
    private final List<Integer> filtered = new ArrayList<>();

    /**
     * Data is passed as primitive arrays rather than the series object so the sheet
     * survives a configuration change and re-draws itself from its own arguments.
     */
    public static BarChartDetailBottomSheet newInstance(String title, ChartStyler.BarSeries series) {
        float[] values = new float[series.size()];
        int[] colors = new int[series.size()];
        boolean[] income = new boolean[series.size()];
        for (int i = 0; i < series.size(); i++) {
            values[i] = series.entries.get(i).getY();
            colors[i] = series.colors.get(i);
            income[i] = Boolean.TRUE.equals(series.income.get(i));
        }

        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putStringArrayList(ARG_LABELS, new ArrayList<>(series.labels));
        args.putFloatArray(ARG_VALUES, values);
        args.putIntArray(ARG_COLORS, colors);
        args.putBooleanArray(ARG_INCOME, income);

        BarChartDetailBottomSheet sheet = new BarChartDetailBottomSheet();
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetBarChartDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        if (args == null || getContext() == null) {
            dismiss();
            return;
        }

        String title = args.getString(ARG_TITLE);
        if (title != null && !title.isEmpty()) {
            binding.txtBarDetailTitle.setText(title);
        }

        ChartStyler.BarSeries series = readSeries(args);

        ChartStyler.applyScrollableBarChartStyle(getContext(), binding.barChartDetail, series, VISIBLE_BARS);
        setupScrollBar(series);
        setupCategorySearch(series);

        binding.btnBarDetailClose.setOnClickListener(v -> dismiss());
    }

    /**
     * Search over every bar in the chart. Scrolling to a specific category gets
     * impractical once the series is long, so this lists them all with their amounts
     * and jumps the chart straight to whichever one is picked.
     */
    private void setupCategorySearch(ChartStyler.BarSeries series) {
        this.series = series;

        adapter = new CategoryAdapter();
        binding.rvCategoryList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvCategoryList.setAdapter(adapter);

        // Results follow the field: focusing it lists everything, typing narrows that
        // down, and leaving an empty field puts the chart back.
        binding.etCategorySearch.setOnFocusChangeListener((v, hasFocus) -> syncResultsVisibility());

        binding.etCategorySearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter(s.toString());
                syncResultsVisibility();
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });

        applyFilter("");
        setResultsVisible(false);
    }

    private void syncResultsVisibility() {
        setResultsVisible(binding.etCategorySearch.hasFocus() || !searchText().isEmpty());
    }

    private String searchText() {
        Editable text = binding.etCategorySearch.getText();
        return text == null ? "" : text.toString();
    }

    /**
     * Single owner of which half of the sheet is showing. The results and the chart swap
     * places rather than stack — together they are taller than the sheet can grow, and
     * putting a list inside a scrolling sheet next to a horizontally draggable chart
     * makes every vertical gesture ambiguous.
     */
    private void setResultsVisible(boolean visible) {
        boolean scrollable = series != null && series.size() > VISIBLE_BARS;

        binding.panelCategoryResults.setVisibility(visible ? View.VISIBLE : View.GONE);
        binding.chartContainer.setVisibility(visible ? View.GONE : View.VISIBLE);
        // The scrollbar and the swipe hint both describe the chart, so they follow it —
        // and stay hidden entirely when the whole series already fits on one screen.
        binding.chartScrollBar.setVisibility(!visible && scrollable ? View.VISIBLE : View.GONE);
        binding.txtBarDetailHint.setVisibility(!visible && scrollable ? View.VISIBLE : View.GONE);
    }

    private void applyFilter(String query) {
        if (binding == null || series == null) return;

        String q = query == null ? "" : query.trim().toLowerCase(Locale.getDefault());
        filtered.clear();
        for (int i = 0; i < series.size(); i++) {
            // Matching the type word too, so "income" or "expense" filters by kind.
            String haystack = (series.labels.get(i) + " " + typeLabel(i)).toLowerCase(Locale.getDefault());
            if (q.isEmpty() || haystack.contains(q)) filtered.add(i);
        }
        adapter.notifyDataSetChanged();

        binding.txtCategoryEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        binding.rvCategoryList.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private String typeLabel(int index) {
        return getString(Boolean.TRUE.equals(series.income.get(index))
                ? R.string.income : R.string.expense);
    }

    /** Returns to the chart with the chosen bar in view and highlighted. */
    private void jumpToBar(int index) {
        // Dropping the query and the focus is what puts the chart back on screen:
        // both feed syncResultsVisibility, and clearing the text re-runs it.
        binding.etCategorySearch.clearFocus();
        binding.etCategorySearch.setText("");
        hideKeyboard();

        float visible = Math.min(VISIBLE_BARS, series.size());
        // moveViewToX anchors to the left edge, so offset by half a screen to centre it.
        binding.barChartDetail.moveViewToX(index - visible / 2f);
        binding.barChartDetail.highlightValue(index, 0);
    }

    private void hideKeyboard() {
        if (getContext() == null || binding == null) return;
        InputMethodManager imm = (InputMethodManager) getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(binding.etCategorySearch.getWindowToken(), 0);
    }

    private class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemChartCategoryBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.bind(filtered.get(position));
        }

        @Override
        public int getItemCount() {
            return filtered.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final ItemChartCategoryBinding b;

            Holder(ItemChartCategoryBinding b) {
                super(b.getRoot());
                this.b = b;
            }

            void bind(int index) {
                b.txtCategoryName.setText(series.labels.get(index));
                b.txtCategoryType.setText(typeLabel(index));
                b.txtCategoryAmount.setText(CurrencyManager.getInstance()
                        .formatAmount(series.entries.get(index).getY()));
                b.viewCategoryDot.setBackgroundTintList(
                        ColorStateList.valueOf(series.colors.get(index)));
                itemView.setOnClickListener(v -> jumpToBar(index));
            }
        }
    }

    /**
     * MPAndroidChart has no scrollbar of its own, so this drives a track-and-thumb
     * pair from the chart's viewport: dragging the chart moves the thumb, and
     * dragging the thumb pans the chart.
     */
    private void setupScrollBar(ChartStyler.BarSeries series) {
        // Everything already fits, so there is nothing to drive. setDropdownOpen keeps
        // the bar itself hidden in that case.
        if (series.size() <= VISIBLE_BARS) return;

        // Syncing on every draw rather than on the chart's gesture callbacks: the
        // viewport isn't narrowed to VISIBLE_BARS until the chart's first layout pass,
        // and this also tracks fling deceleration, which fires no gesture events.
        preDrawSync = () -> {
            syncScrollBar();
            return true;
        };
        binding.barChartDetail.getViewTreeObserver().addOnPreDrawListener(preDrawSync);

        binding.chartScrollBar.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    // Claim the gesture so the bottom sheet doesn't read it as a swipe-to-dismiss.
                    view.getParent().requestDisallowInterceptTouchEvent(true);
                    panChartTo(event.getX());
                    return true;
                case MotionEvent.ACTION_MOVE:
                    panChartTo(event.getX());
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                default:
                    return false;
            }
        });
    }

    /** Resizes and repositions the thumb to match the chart's current viewport. */
    private void syncScrollBar() {
        if (binding == null) return;

        float axisMin = binding.barChartDetail.getXAxis().getAxisMinimum();
        float total = binding.barChartDetail.getXAxis().getAxisMaximum() - axisMin;
        float visible = binding.barChartDetail.getHighestVisibleX()
                - binding.barChartDetail.getLowestVisibleX();
        int trackWidth = binding.chartScrollBar.getWidth();
        if (total <= 0f || visible <= 0f || trackWidth <= 0) return;

        int minThumb = Math.round(MIN_THUMB_WIDTH_DP * getResources().getDisplayMetrics().density);
        int thumbWidth = Math.max(minThumb, Math.round(trackWidth * Math.min(1f, visible / total)));
        View thumb = binding.chartScrollThumb;
        if (thumb.getLayoutParams().width != thumbWidth) {
            thumb.getLayoutParams().width = thumbWidth;
            thumb.requestLayout();
        }

        float pannableRange = total - visible;
        float progress = pannableRange <= 0f ? 0f
                : clamp((binding.barChartDetail.getLowestVisibleX() - axisMin) / pannableRange);
        thumb.setTranslationX((trackWidth - thumbWidth) * progress);
    }

    /** Maps a touch on the track to a chart x-offset, treating the touch as the thumb's centre. */
    private void panChartTo(float touchX) {
        if (binding == null) return;

        float axisMin = binding.barChartDetail.getXAxis().getAxisMinimum();
        float total = binding.barChartDetail.getXAxis().getAxisMaximum() - axisMin;
        float visible = binding.barChartDetail.getHighestVisibleX()
                - binding.barChartDetail.getLowestVisibleX();
        float pannableRange = total - visible;
        int thumbWidth = binding.chartScrollThumb.getWidth();
        int travel = binding.chartScrollBar.getWidth() - thumbWidth;
        if (pannableRange <= 0f || travel <= 0) return;

        float progress = clamp((touchX - thumbWidth / 2f) / travel);
        // moveViewToX puts the given value at the left edge of the viewport. The thumb
        // is deliberately not moved here — syncScrollBar owns its position, so the two
        // can't fight over it mid-drag.
        binding.barChartDetail.moveViewToX(axisMin + progress * pannableRange);
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private ChartStyler.BarSeries readSeries(Bundle args) {
        List<String> labels = args.getStringArrayList(ARG_LABELS);
        float[] values = args.getFloatArray(ARG_VALUES);
        int[] colors = args.getIntArray(ARG_COLORS);
        boolean[] income = args.getBooleanArray(ARG_INCOME);

        ChartStyler.BarSeries series = new ChartStyler.BarSeries();
        if (labels == null || values == null || colors == null || income == null) return series;

        int count = Math.min(Math.min(labels.size(), income.length),
                Math.min(values.length, colors.length));
        for (int i = 0; i < count; i++) {
            series.entries.add(new BarEntry(i, values[i]));
            series.labels.add(labels.get(i));
            series.colors.add(colors[i]);
            series.income.add(income[i]);
        }
        return series;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() == null) return;

        // The sheet opens on the chart, not the keyboard — the search field is there to
        // be used when wanted, and focusing it would swap the chart out immediately.
        if (getDialog().getWindow() != null) {
            getDialog().getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        }

        // Open fully expanded — a half-height peek would clip the chart it exists to show.
        View sheet = getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) {
            BottomSheetBehavior.from(sheet).setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    @Override
    public void onDestroyView() {
        if (binding != null && preDrawSync != null) {
            binding.barChartDetail.getViewTreeObserver().removeOnPreDrawListener(preDrawSync);
        }
        preDrawSync = null;
        super.onDestroyView();
        binding = null;
    }
}
