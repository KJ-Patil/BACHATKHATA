package com.example.moneylens;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.moneylens.databinding.ActivityCalendarBinding;
import com.example.moneylens.domain.CalendarUtil;
import com.example.moneylens.domain.DueDates;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CalendarActivity extends BaseActivity {

    private ActivityCalendarBinding binding;
    private FirebaseFirestore mFirestore;
    private FirebaseAuth mAuth;

    private int viewYear;
    private int viewMonth; // 1-based

    private final List<Transaction> allTransactions = new ArrayList<>();
    private Map<String, List<DueDates.DueItem>> dueByDay = new HashMap<>();
    private final Map<String, Double> spendByDay = new HashMap<>();
    private final Map<String, Double> incomeByDay = new HashMap<>();
    private final Set<String> ledgerDays = new HashSet<>();
    private double maxDaySpend = 0d;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCalendarBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mFirestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        LocalDate today = CalendarUtil.today();
        viewYear = today.getYear();
        viewMonth = today.getMonthValue();

        setupUI();
        loadData();
    }

    private void setupUI() {
        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnPrevMonth.setOnClickListener(v -> {
            if (viewMonth == 1) {
                viewMonth = 12;
                viewYear--;
            } else {
                viewMonth--;
            }
            refreshGrid();
            updateSummary();
        });

        binding.btnNextMonth.setOnClickListener(v -> {
            if (viewMonth == 12) {
                viewMonth = 1;
                viewYear++;
            } else {
                viewMonth++;
            }
            refreshGrid();
            updateSummary();
        });

        binding.btnToday.setOnClickListener(v -> {
            LocalDate today = CalendarUtil.today();
            viewYear = today.getYear();
            viewMonth = today.getMonthValue();
            refreshGrid();
            updateSummary();
        });
    }

    private void loadData() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot> txnsTask =
                mFirestore.collection("users").document(uid).collection("transactions").get();
        com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot> emisTask =
                mFirestore.collection("users").document(uid).collection("emis").get();
        com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot> subsTask =
                mFirestore.collection("users").document(uid).collection("subscriptions").get();

        com.google.android.gms.tasks.Tasks.whenAllComplete(txnsTask, emisTask, subsTask)
                .addOnCompleteListener(t -> {
                    processTransactions(txnsTask.isSuccessful() ? txnsTask.getResult() : null);
                    buildDueProjection(
                            emisTask.isSuccessful() ? emisTask.getResult() : null,
                            subsTask.isSuccessful() ? subsTask.getResult() : null);
                    refreshGrid();
                    updateSummary();
                });
    }

    private void processTransactions(com.google.firebase.firestore.QuerySnapshot snapshot) {
        allTransactions.clear();
        spendByDay.clear();
        incomeByDay.clear();
        ledgerDays.clear();
        maxDaySpend = 0d;

        if (snapshot == null) return;

        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            Transaction tx = Transaction.fromDocument(doc);
            allTransactions.add(tx);

            if (tx.getDate() == null) continue;
            LocalDate date = tx.getDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            String key = CalendarUtil.toDateKey(date);

            boolean isLedger = LedgerMirror.LEDGER_CATEGORY.equals(tx.getCategory());

            if (isLedger) {
                ledgerDays.add(key);
            } else if ("expense".equals(tx.getType())) {
                double running = spendByDay.getOrDefault(key, 0d) + tx.getAmount();
                spendByDay.put(key, running);
                if (running > maxDaySpend) maxDaySpend = running;
            } else if ("income".equals(tx.getType())) {
                incomeByDay.merge(key, tx.getAmount(), Double::sum);
            }
        }
    }

    private void buildDueProjection(com.google.firebase.firestore.QuerySnapshot emis,
                                    com.google.firebase.firestore.QuerySnapshot subs) {
        List<DueDates.LoanInput> loans = new ArrayList<>();
        if (emis != null) {
            for (DocumentSnapshot doc : emis.getDocuments()) {
                LocalDate start = toLocalDate(doc.get("startDate"));
                Long tenure = doc.getLong("tenureMonths");
                Double emi = doc.getDouble("emiAmount");
                if (start == null || tenure == null || emi == null) continue;
                String name = doc.getString("loanName");
                loans.add(new DueDates.LoanInput(
                        name != null ? name : "EMI", emi, tenure.intValue(), start));
            }
        }

        List<DueDates.SubscriptionInput> subscriptions = new ArrayList<>();
        if (subs != null) {
            for (DocumentSnapshot doc : subs.getDocuments()) {
                Boolean active = doc.getBoolean("isActive");
                if (active != null && !active) continue;
                LocalDate next = toLocalDate(doc.get("nextRenewalDate"));
                Double amount = doc.getDouble("amount");
                if (next == null || amount == null) continue;
                String name = doc.getString("name");
                subscriptions.add(new DueDates.SubscriptionInput(
                        name != null ? name : "Subscription", amount, next));
            }
        }

        dueByDay = DueDates.compute(loans, subscriptions, LocalDate.now());
    }

    private void refreshGrid() {
        binding.txtCurrentMonth.setText(
                CalendarUtil.MONTH_LABELS.get(viewMonth - 1) + " " + viewYear);

        List<LocalDate> cells = CalendarUtil.buildMonthGrid(viewYear, viewMonth);
        MonthGridAdapter adapter = new MonthGridAdapter(cells);
        binding.gridCalendar.setAdapter(adapter);

        binding.gridCalendar.setOnItemClickListener((parent, view, position, id) -> {
            LocalDate date = cells.get(position);
            if (date.getMonthValue() != viewMonth) return;

            Intent intent = new Intent(CalendarActivity.this, CalendarDayDetailActivity.class);
            intent.putExtra("dateKey", CalendarUtil.toDateKey(date));
            startActivity(intent);
        });
    }

    private void updateSummary() {
        double monthIncome = 0;
        double monthExpense = 0;
        int txCount = 0;

        for (Transaction tx : allTransactions) {
            if (tx.getDate() == null) continue;
            LocalDate date = tx.getDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            if (date.getYear() != viewYear || date.getMonthValue() != viewMonth) continue;
            if (LedgerMirror.LEDGER_CATEGORY.equals(tx.getCategory())) continue;

            txCount++;
            if ("income".equals(tx.getType())) {
                monthIncome += tx.getAmount();
            } else if ("expense".equals(tx.getType())) {
                monthExpense += tx.getAmount();
            }
        }

        CurrencyManager cm = CurrencyManager.getInstance();
        binding.txtMonthIncome.setText(cm.formatAmount(monthIncome));
        binding.txtMonthExpense.setText(cm.formatAmount(monthExpense));
        binding.txtMonthTxCount.setText(String.valueOf(txCount));
    }

    private void applyHeatmapTint(View cell, String dayKey) {
        double spend = spendByDay.getOrDefault(dayKey, 0d);
        if (spend <= 0 || maxDaySpend <= 0) {
            cell.setBackground(null);
            return;
        }
        double intensity = 0.06 + (spend / maxDaySpend) * 0.22;
        int alpha = (int) Math.round(Math.min(intensity, 0.28) * 255);
        cell.setBackgroundResource(R.drawable.bg_clay_button);
        cell.setBackgroundTintList(ColorStateList.valueOf(
                Color.argb(alpha, 0xDC, 0x26, 0x26)));
    }

    private LocalDate toLocalDate(Object value) {
        Date date = null;
        if (value instanceof Timestamp) {
            date = ((Timestamp) value).toDate();
        } else if (value instanceof Date) {
            date = (Date) value;
        } else if (value instanceof Long) {
            date = new Date((Long) value);
        }
        if (date == null) return null;
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    // ── Grid Adapter ──────────────────────────────────────────────────────

    private class MonthGridAdapter extends BaseAdapter {
        private final List<LocalDate> cells;
        private final LocalDate today = CalendarUtil.today();

        MonthGridAdapter(List<LocalDate> cells) {
            this.cells = cells;
        }

        @Override
        public int getCount() { return cells.size(); }

        @Override
        public Object getItem(int position) { return cells.get(position); }

        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_calendar_cell, parent, false);
            }

            LocalDate date = cells.get(position);
            String dayKey = CalendarUtil.toDateKey(date);
            boolean isCurrentMonth = date.getMonthValue() == viewMonth;
            boolean isToday = CalendarUtil.isSameDay(date, today);

            TextView txtDay = convertView.findViewById(R.id.txtDayNumber);
            View layoutDots = convertView.findViewById(R.id.layoutDots);
            View dotGreen = convertView.findViewById(R.id.dotGreen);
            View dotRed = convertView.findViewById(R.id.dotRed);
            View dotBlue = convertView.findViewById(R.id.dotBlue);
            View dotPurple = convertView.findViewById(R.id.dotPurple);
            View dotAmber = convertView.findViewById(R.id.dotAmber);

            txtDay.setText(String.valueOf(date.getDayOfMonth()));

            if (!isCurrentMonth) {
                txtDay.setAlpha(0.3f);
                layoutDots.setVisibility(View.GONE);
                convertView.setBackground(null);
                convertView.setBackgroundTintList(null);
                return convertView;
            }

            txtDay.setAlpha(1f);
            layoutDots.setVisibility(View.VISIBLE);

            // Today highlight
            if (isToday) {
                convertView.setBackgroundResource(R.drawable.bg_today_pill);
                convertView.setBackgroundTintList(null);
                txtDay.setTextColor(getColor(R.color.colorPrimary));
            } else {
                applyHeatmapTint(convertView, dayKey);
                txtDay.setTextColor(getColor(R.color.colorTextPrimary));
            }

            // Dot indicators
            boolean hasIncome = incomeByDay.containsKey(dayKey);
            boolean hasExpense = spendByDay.containsKey(dayKey);
            boolean hasLedger = ledgerDays.contains(dayKey);
            boolean hasDue = dueByDay.containsKey(dayKey);

            dotGreen.setVisibility(hasIncome ? View.VISIBLE : View.GONE);
            dotRed.setVisibility(hasExpense ? View.VISIBLE : View.GONE);
            dotBlue.setVisibility(hasLedger ? View.VISIBLE : View.GONE);
            dotPurple.setVisibility(hasDue ? View.VISIBLE : View.GONE);
            dotAmber.setVisibility(View.GONE);

            return convertView;
        }
    }
}
