package com.example.bachatkhata;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;

import com.example.bachatkhata.databinding.ActivityComparisonBinding;
import com.example.bachatkhata.databinding.ItemComparisonRowBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Month-vs-month spend comparison. Distinct from Analytics: it diffs the current
 * month against the previous month per category, showing the absolute delta and
 * percentage change (↑/↓) for each. Expenses only.
 */
public class ComparisonActivity extends BaseActivity {

    private static final int UP_COLOR = Color.parseColor("#E24B4A");   // spending rose (bad)
    private static final int DOWN_COLOR = Color.parseColor("#5DCAA5"); // spending fell (good)

    private ActivityComparisonBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityComparisonBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            loadComparison(auth.getCurrentUser().getUid());
        }
    }

    private void loadComparison(String uid) {
        Calendar cal = Calendar.getInstance();
        // Start of previous month — the earliest data we need.
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.MONTH, -1);
        Date startOfPrevMonth = cal.getTime();

        Calendar nowCal = Calendar.getInstance();
        int curMonth = nowCal.get(Calendar.MONTH);
        int curYear = nowCal.get(Calendar.YEAR);

        Calendar prevCal = Calendar.getInstance();
        prevCal.add(Calendar.MONTH, -1);
        int prevMonth = prevCal.get(Calendar.MONTH);
        int prevYear = prevCal.get(Calendar.YEAR);

        FirebaseFirestore.getInstance()
                .collection("users").document(uid).collection("transactions")
                .whereGreaterThanOrEqualTo("date", startOfPrevMonth)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (binding == null) return;

                    Map<String, Double> current = new HashMap<>();
                    Map<String, Double> previous = new HashMap<>();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Transaction t = Transaction.fromDocument(doc);
                        if (t.getDate() == null || !"expense".equalsIgnoreCase(t.getType())) continue;

                        Calendar c = Calendar.getInstance();
                        c.setTime(t.getDate());
                        String cat = t.getCategory() != null ? t.getCategory() : "Other";

                        if (c.get(Calendar.MONTH) == curMonth && c.get(Calendar.YEAR) == curYear) {
                            current.put(cat, current.getOrDefault(cat, 0.0) + t.getAmount());
                        } else if (c.get(Calendar.MONTH) == prevMonth && c.get(Calendar.YEAR) == prevYear) {
                            previous.put(cat, previous.getOrDefault(cat, 0.0) + t.getAmount());
                        }
                    }

                    render(current, previous, nowCal, prevCal);
                })
                .addOnFailureListener(e -> {
                    if (binding != null) binding.txtEmpty.setVisibility(View.VISIBLE);
                });
    }

    private void render(Map<String, Double> current, Map<String, Double> previous,
                        Calendar nowCal, Calendar prevCal) {
        CurrencyManager cm = CurrencyManager.getInstance();

        double curTotal = sum(current);
        double prevTotal = sum(previous);

        binding.txtCurrentMonthLabel.setText(monthLabel(nowCal));
        binding.txtPreviousMonthLabel.setText(monthLabel(prevCal));
        binding.txtCurrentMonthTotal.setText(cm.formatAmount(curTotal));
        binding.txtPreviousMonthTotal.setText(cm.formatAmount(prevTotal));

        double overallDelta = curTotal - prevTotal;
        binding.txtOverallDelta.setText(formatDelta(overallDelta, cm) + "  (" + formatPct(prevTotal, curTotal) + ")");
        binding.txtOverallDelta.setTextColor(overallDelta > 0 ? UP_COLOR : DOWN_COLOR);

        // Union of all categories appearing in either month.
        TreeSet<String> categories = new TreeSet<>();
        categories.addAll(current.keySet());
        categories.addAll(previous.keySet());

        List<Row> rows = new ArrayList<>();
        for (String cat : categories) {
            double cur = current.getOrDefault(cat, 0.0);
            double prev = previous.getOrDefault(cat, 0.0);
            rows.add(new Row(cat, cur, prev));
        }
        // Biggest absolute change first.
        Collections.sort(rows, (a, b) -> Double.compare(Math.abs(b.delta()), Math.abs(a.delta())));

        binding.layoutRows.removeAllViews();
        if (rows.isEmpty()) {
            binding.txtEmpty.setVisibility(View.VISIBLE);
            return;
        }
        binding.txtEmpty.setVisibility(View.GONE);

        for (Row row : rows) {
            ItemComparisonRowBinding rb = ItemComparisonRowBinding.inflate(
                    getLayoutInflater(), binding.layoutRows, false);

            rb.txtCategory.setText(row.category);
            rb.txtCurrentAmount.setText(cm.formatAmount(row.current));
            rb.txtPreviousAmount.setText("was " + cm.formatAmount(row.previous));

            double delta = row.delta();
            boolean up = delta > 0;
            int color = Math.abs(delta) < 0.01 ? Color.parseColor("#9AA0A6") : (up ? UP_COLOR : DOWN_COLOR);
            String arrow = Math.abs(delta) < 0.01 ? "→" : (up ? "↑" : "↓");

            rb.txtDelta.setText(arrow + " " + cm.formatAmount(Math.abs(delta)));
            rb.txtDelta.setTextColor(color);
            rb.txtPct.setText(formatPct(row.previous, row.current));
            rb.txtPct.setTextColor(color);

            binding.layoutRows.addView(rb.getRoot());
        }
    }

    private double sum(Map<String, Double> map) {
        double s = 0;
        for (double v : map.values()) s += v;
        return s;
    }

    private String monthLabel(Calendar cal) {
        return cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.US) + " " + cal.get(Calendar.YEAR);
    }

    private String formatDelta(double delta, CurrencyManager cm) {
        String sign = delta > 0 ? "+" : (delta < 0 ? "-" : "");
        return sign + cm.formatAmount(Math.abs(delta));
    }

    /** Percentage change from prev → cur. */
    private String formatPct(double prev, double cur) {
        if (prev <= 0.0) {
            return cur > 0.0 ? "new" : "0%";
        }
        double pct = ((cur - prev) / prev) * 100.0;
        String sign = pct > 0 ? "+" : "";
        return sign + String.format(Locale.US, "%.0f%%", pct);
    }

    private static class Row {
        final String category;
        final double current;
        final double previous;

        Row(String category, double current, double previous) {
            this.category = category;
            this.current = current;
            this.previous = previous;
        }

        double delta() {
            return current - previous;
        }
    }
}
