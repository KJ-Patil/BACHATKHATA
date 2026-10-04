package com.example.moneylens;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.moneylens.databinding.ActivityCalendarDayDetailBinding;
import com.example.moneylens.domain.CalendarUtil;
import com.example.moneylens.domain.DueDates;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CalendarDayDetailActivity extends BaseActivity {

    private ActivityCalendarDayDetailBinding binding;
    private FirebaseFirestore mFirestore;
    private FirebaseAuth mAuth;

    private LocalDate targetDate;
    private String dateKey;

    private final List<Transaction> dayTransactions = new ArrayList<>();
    private final List<Transaction> dayLedgerEntries = new ArrayList<>();
    private List<DueDates.DueItem> dayDueItems = new ArrayList<>();

    private final List<Transaction> filteredTransactions = new ArrayList<>();
    private final List<Transaction> filteredLedger = new ArrayList<>();

    private DayEntryAdapter txAdapter;
    private DayEntryAdapter ledgerAdapter;
    private DueItemAdapter dueAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCalendarDayDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mFirestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        dateKey = getIntent().getStringExtra("dateKey");
        targetDate = CalendarUtil.parseDateKey(dateKey);
        if (targetDate == null) {
            finish();
            return;
        }

        DateTimeFormatter titleFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault());
        binding.txtTitle.setText(targetDate.format(titleFmt));

        setupRecyclerViews();
        setupSearch();
        loadData();
    }

    private void setupRecyclerViews() {
        txAdapter = new DayEntryAdapter(filteredTransactions, false);
        binding.rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        binding.rvTransactions.setAdapter(txAdapter);

        ledgerAdapter = new DayEntryAdapter(filteredLedger, true);
        binding.rvLedgerEntries.setLayoutManager(new LinearLayoutManager(this));
        binding.rvLedgerEntries.setAdapter(ledgerAdapter);

        dueAdapter = new DueItemAdapter(dayDueItems);
        binding.rvDueItems.setLayoutManager(new LinearLayoutManager(this));
        binding.rvDueItems.setAdapter(dueAdapter);

        binding.btnBack.setOnClickListener(v -> finish());
    }

    private void setupSearch() {
        binding.editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                filterEntries(s.toString().trim().toLowerCase(Locale.ROOT));
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void filterEntries(String query) {
        filteredTransactions.clear();
        filteredLedger.clear();

        if (query.isEmpty()) {
            filteredTransactions.addAll(dayTransactions);
            filteredLedger.addAll(dayLedgerEntries);
        } else {
            for (Transaction tx : dayTransactions) {
                if (matches(tx, query)) filteredTransactions.add(tx);
            }
            for (Transaction tx : dayLedgerEntries) {
                if (matches(tx, query)) filteredLedger.add(tx);
            }
        }

        txAdapter.notifyDataSetChanged();
        ledgerAdapter.notifyDataSetChanged();
        updateKPIs();
        updateVisibility();
    }

    private boolean matches(Transaction tx, String query) {
        String cat = tx.getCategory();
        String note = tx.getNote();
        if (cat != null && cat.toLowerCase(Locale.ROOT).contains(query)) return true;
        if (note != null && note.toLowerCase(Locale.ROOT).contains(query)) return true;
        return false;
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
                    buildDueItems(
                            emisTask.isSuccessful() ? emisTask.getResult() : null,
                            subsTask.isSuccessful() ? subsTask.getResult() : null);
                    filterEntries("");
                });
    }

    private void processTransactions(com.google.firebase.firestore.QuerySnapshot snapshot) {
        dayTransactions.clear();
        dayLedgerEntries.clear();

        if (snapshot == null) return;

        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            Transaction tx = Transaction.fromDocument(doc);
            if (tx.getDate() == null) continue;

            LocalDate txDate = tx.getDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            if (!CalendarUtil.isSameDay(txDate, targetDate)) continue;

            if (LedgerMirror.LEDGER_CATEGORY.equals(tx.getCategory())) {
                dayLedgerEntries.add(tx);
            } else {
                dayTransactions.add(tx);
            }
        }
    }

    private void buildDueItems(com.google.firebase.firestore.QuerySnapshot emis,
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

        Map<String, List<DueDates.DueItem>> byDay =
                DueDates.compute(loans, subscriptions, LocalDate.now());
        List<DueDates.DueItem> items = byDay.get(dateKey);
        dayDueItems.clear();
        if (items != null) dayDueItems.addAll(items);
        dueAdapter.notifyDataSetChanged();
    }

    private void updateKPIs() {
        double income = 0;
        double expense = 0;

        // Income = income txs + ledger "got" entries
        for (Transaction tx : filteredTransactions) {
            if ("income".equals(tx.getType())) income += tx.getAmount();
            else if ("expense".equals(tx.getType())) expense += tx.getAmount();
        }
        for (Transaction tx : filteredLedger) {
            // Ledger mirrors: "got" -> income, "gave" -> expense
            if ("income".equals(tx.getType())) income += tx.getAmount();
            else if ("expense".equals(tx.getType())) expense += tx.getAmount();
        }

        CurrencyManager cm = CurrencyManager.getInstance();
        binding.txtDayIncome.setText(cm.formatAmount(income));
        binding.txtDayExpense.setText(cm.formatAmount(expense));

        double net = income - expense;
        binding.txtDayNet.setText(cm.formatAmount(Math.abs(net)));
        if (net >= 0) {
            binding.txtDayNet.setTextColor(getColor(R.color.colorSuccess));
        } else {
            binding.txtDayNet.setTextColor(getColor(R.color.colorDanger));
        }
    }

    private void updateVisibility() {
        boolean hasTx = !filteredTransactions.isEmpty();
        boolean hasLedger = !filteredLedger.isEmpty();
        boolean hasDue = !dayDueItems.isEmpty();

        binding.sectionTransactions.setVisibility(hasTx ? View.VISIBLE : View.GONE);
        binding.sectionLedger.setVisibility(hasLedger ? View.VISIBLE : View.GONE);
        binding.sectionDue.setVisibility(hasDue ? View.VISIBLE : View.GONE);

        binding.layoutEmptyState.setVisibility(
                (!hasTx && !hasLedger && !hasDue) ? View.VISIBLE : View.GONE);
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

    // ── Transaction / Ledger entry adapter ────────────────────────────────

    private class DayEntryAdapter extends RecyclerView.Adapter<DayEntryAdapter.VH> {
        private final List<Transaction> items;
        private final boolean isLedger;

        DayEntryAdapter(List<Transaction> items, boolean isLedger) {
            this.items = items;
            this.isLedger = isLedger;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_day_entry, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Transaction tx = items.get(position);
            CurrencyManager cm = CurrencyManager.getInstance();

            String title;
            if (isLedger) {
                String note = tx.getNote();
                title = (note != null && !note.isEmpty()) ? note : "Notebook Entry";
            } else {
                title = tx.getCategory() != null ? tx.getCategory() : "Uncategorized";
            }
            holder.txtTitle.setText(title);

            String subtitle = isLedger
                    ? ("expense".equals(tx.getType()) ? "You Gave" : "You Got")
                    : (tx.getNote() != null && !tx.getNote().isEmpty() ? tx.getNote() : tx.getType());
            holder.txtSubtitle.setText(subtitle);

            holder.txtAmount.setText(cm.formatAmount(tx.getAmount()));

            boolean isExpense = "expense".equals(tx.getType());
            int amountColor = isExpense ? getColor(R.color.colorDanger) : getColor(R.color.colorSuccess);
            holder.txtAmount.setTextColor(amountColor);

            int dotColor = isLedger ? getColor(R.color.colorAccent) : amountColor;
            holder.viewIndicator.setBackgroundTintList(ColorStateList.valueOf(dotColor));
        }

        @Override
        public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            final TextView txtTitle, txtSubtitle, txtAmount;
            final View viewIndicator;

            VH(View v) {
                super(v);
                txtTitle = v.findViewById(R.id.txtEntryTitle);
                txtSubtitle = v.findViewById(R.id.txtEntrySubtitle);
                txtAmount = v.findViewById(R.id.txtAmount);
                viewIndicator = v.findViewById(R.id.viewTypeIndicator);
            }
        }
    }

    // ── Due item adapter ──────────────────────────────────────────────────

    private class DueItemAdapter extends RecyclerView.Adapter<DueItemAdapter.VH> {
        private final List<DueDates.DueItem> items;

        DueItemAdapter(List<DueDates.DueItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_day_entry, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            DueDates.DueItem item = items.get(position);
            CurrencyManager cm = CurrencyManager.getInstance();

            holder.txtTitle.setText(item.label);
            holder.txtSubtitle.setText(
                    DueDates.KIND_EMI.equals(item.kind) ? "EMI Payment" : "Subscription");
            holder.txtAmount.setText(cm.formatAmount(item.amount));
            holder.txtAmount.setTextColor(getColor(R.color.colorPrimary));
            holder.viewIndicator.setBackgroundTintList(
                    ColorStateList.valueOf(getColor(R.color.colorPrimary)));
        }

        @Override
        public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            final TextView txtTitle, txtSubtitle, txtAmount;
            final View viewIndicator;

            VH(View v) {
                super(v);
                txtTitle = v.findViewById(R.id.txtEntryTitle);
                txtSubtitle = v.findViewById(R.id.txtEntrySubtitle);
                txtAmount = v.findViewById(R.id.txtAmount);
                viewIndicator = v.findViewById(R.id.viewTypeIndicator);
            }
        }
    }
}
