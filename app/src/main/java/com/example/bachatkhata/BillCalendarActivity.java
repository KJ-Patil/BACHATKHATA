package com.example.bachatkhata;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bachatkhata.databinding.ActivityBillCalendarBinding;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BillCalendarActivity extends BaseActivity {

    private ActivityBillCalendarBinding binding;
    private FirebaseFirestore mFirestore;
    private FirebaseAuth mAuth;
    private ListenerRegistration billsListener;

    private final List<DocumentSnapshot> allBills = new ArrayList<>();
    private final List<DocumentSnapshot> filteredBills = new ArrayList<>();
    private BillListAdapter billListAdapter;

    private Calendar currentCalendar;
    private int selectedDay = -1; // -1 means show all bills for this month

    private final SimpleDateFormat monthYearFormat = new SimpleDateFormat("MMMM yyyy", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBillCalendarBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mFirestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        currentCalendar = Calendar.getInstance();

        setupUI();
        observeBills();
    }

    private void setupUI() {
        binding.btnBack.setOnClickListener(v -> finish());

        // Month Navigation
        binding.btnPrevMonth.setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, -1);
            selectedDay = -1;
            updateCalendar();
            updateBillsList();
        });

        binding.btnNextMonth.setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, 1);
            selectedDay = -1;
            updateCalendar();
            updateBillsList();
        });

        // FAB to add new bills
        binding.fabAddBill.setOnClickListener(v -> {
            AddBillBottomSheet bottomSheet = new AddBillBottomSheet();
            bottomSheet.setOnDismissCallback(this::observeBills); // Refresh lists when dismissed
            bottomSheet.show(getSupportFragmentManager(), "AddBillBottomSheet");
        });

        // RecyclerView setup
        binding.rvBills.setLayoutManager(new LinearLayoutManager(this));
        billListAdapter = new BillListAdapter();
        binding.rvBills.setAdapter(billListAdapter);

        updateCalendar();
    }

    private void observeBills() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        if (billsListener != null) {
            billsListener.remove();
        }

        billsListener = mFirestore.collection("users").document(uid).collection("bills")
                .whereEqualTo("isActive", true)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        showSnackbar("Error loading bills: " + error.getLocalizedMessage(), "ERROR");
                        return;
                    }

                    allBills.clear();
                    if (value != null) {
                        allBills.addAll(value.getDocuments());
                    }
                    updateCalendar();
                    updateBillsList();
                });
    }

    private void updateCalendar() {
        binding.txtCurrentMonth.setText(monthYearFormat.format(currentCalendar.getTime()));

        // Prepare calendar grid values
        Calendar cal = (Calendar) currentCalendar.clone();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK); // Sunday = 1, Monday = 2
        int maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        // Calculate offset (Sunday start)
        int paddingOffset = firstDayOfWeek - Calendar.SUNDAY;

        List<Integer> daysList = new ArrayList<>();
        // Padding cells
        for (int i = 0; i < paddingOffset; i++) {
            daysList.add(0);
        }
        // Current month cells
        for (int i = 1; i <= maxDay; i++) {
            daysList.add(i);
        }
        // Wrap to multiples of 7
        while (daysList.size() % 7 != 0) {
            daysList.add(0);
        }

        CalendarAdapter adapter = new CalendarAdapter(daysList);
        binding.gridCalendar.setAdapter(adapter);

        binding.gridCalendar.setOnItemClickListener((parent, view, position, id) -> {
            int day = daysList.get(position);
            if (day > 0) {
                if (selectedDay == day) {
                    selectedDay = -1; // toggle off
                } else {
                    selectedDay = day;
                }
                updateCalendar();
                updateBillsList();
            }
        });
    }

    private void updateBillsList() {
        filteredBills.clear();

        int viewedMonth = currentCalendar.get(Calendar.MONTH);
        int viewedYear = currentCalendar.get(Calendar.YEAR);

        for (DocumentSnapshot doc : allBills) {
            Integer dueDay = doc.getLong("dueDay") != null ? doc.getLong("dueDay").intValue() : null;
            if (dueDay != null) {
                if (selectedDay == -1 || dueDay == selectedDay) {
                    filteredBills.add(doc);
                }
            }
        }

        // Sort by due day ascending
        Collections.sort(filteredBills, (o1, o2) -> {
            Long day1 = o1.getLong("dueDay");
            Long day2 = o2.getLong("dueDay");
            int val1 = day1 != null ? day1.intValue() : 0;
            int val2 = day2 != null ? day2.intValue() : 0;
            return Integer.compare(val1, val2);
        });

        billListAdapter.notifyDataSetChanged();

        if (selectedDay == -1) {
            binding.txtBillsHeader.setText("All Bills for " + monthYearFormat.format(currentCalendar.getTime()));
        } else {
            binding.txtBillsHeader.setText(String.format(Locale.US, "Bills due on %d %s", selectedDay, monthYearFormat.format(currentCalendar.getTime())));
        }

        if (filteredBills.isEmpty()) {
            binding.rvBills.setVisibility(View.GONE);
            binding.layoutEmptyState.setVisibility(View.VISIBLE);
        } else {
            binding.rvBills.setVisibility(View.VISIBLE);
            binding.layoutEmptyState.setVisibility(View.GONE);
        }
    }

    private boolean isBillPaidThisMonth(DocumentSnapshot billDoc, int month, int year) {
        Object lastPaidObj = billDoc.get("lastPaidDate");
        if (lastPaidObj == null) {
            return false;
        }

        Date lastPaidDate = null;
        if (lastPaidObj instanceof Timestamp) {
            lastPaidDate = ((Timestamp) lastPaidObj).toDate();
        } else if (lastPaidObj instanceof Long) {
            lastPaidDate = new Date((Long) lastPaidObj);
        } else if (lastPaidObj instanceof Date) {
            lastPaidDate = (Date) lastPaidObj;
        }

        if (lastPaidDate == null) {
            return false;
        }

        Calendar lastPaidCal = Calendar.getInstance();
        lastPaidCal.setTime(lastPaidDate);
        return lastPaidCal.get(Calendar.MONTH) == month && lastPaidCal.get(Calendar.YEAR) == year;
    }

    private String getBillStatus(DocumentSnapshot billDoc, int dayNum) {
        int viewedMonth = currentCalendar.get(Calendar.MONTH);
        int viewedYear = currentCalendar.get(Calendar.YEAR);

        if (isBillPaidThisMonth(billDoc, viewedMonth, viewedYear)) {
            return "PAID";
        }

        Calendar today = Calendar.getInstance();
        Calendar targetDate = Calendar.getInstance();
        targetDate.set(Calendar.YEAR, viewedYear);
        targetDate.set(Calendar.MONTH, viewedMonth);
        targetDate.set(Calendar.DAY_OF_MONTH, dayNum);
        targetDate.set(Calendar.HOUR_OF_DAY, 23);
        targetDate.set(Calendar.MINUTE, 59);

        if (targetDate.before(today)) {
            return "OVERDUE";
        }

        // Within 3 days
        long diffMs = targetDate.getTimeInMillis() - today.getTimeInMillis();
        long diffDays = diffMs / (24 * 60 * 60 * 1000);
        if (diffDays >= 0 && diffDays <= 3) {
            return "DUE_SOON";
        }

        return "UPCOMING";
    }

    private void markBillAsPaid(DocumentSnapshot billDoc) {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        String billId = billDoc.getString("id");
        String billName = billDoc.getString("name");
        Double amount = billDoc.getDouble("amount");
        String rawCategory = billDoc.getString("category");
        final String category = rawCategory != null ? rawCategory : "Bills";
        final double finalAmount = amount != null ? amount : 0.0;

        new AlertDialog.Builder(this)
                .setTitle("Mark Bill Paid")
                .setMessage(String.format("Do you want to mark '%s' as paid for %s? This will log a corresponding expense transaction.", billName, monthYearFormat.format(currentCalendar.getTime())))
                .setPositiveButton("Yes, Paid", (dialog, which) -> {
                    WriteBatch batch = mFirestore.batch();

                    // 1. Update bill's lastPaidDate
                    DocumentReference billRef = mFirestore.collection("users").document(uid).collection("bills").document(billId);
                    batch.update(billRef, "lastPaidDate", Timestamp.now());

                    // 2. Add an automatic expense transaction
                    DocumentReference txnRef = mFirestore.collection("users").document(uid).collection("transactions").document();
                    Map<String, Object> txnMap = new HashMap<>();
                    txnMap.put("id", txnRef.getId());
                    txnMap.put("amount", finalAmount);
                    txnMap.put("type", "expense");
                    txnMap.put("category", category);
                    txnMap.put("note", "Paid: " + billName + " (" + monthYearFormat.format(currentCalendar.getTime()) + ")");
                    txnMap.put("date", new Timestamp(new Date()));
                    txnMap.put("account", "Bank");
                    txnMap.put("currency", CurrencyManager.getInstance().getCurrentCurrencyCode());
                    txnMap.put("currencySymbol", CurrencyManager.getInstance().getCurrentCurrencySymbol());
                    txnMap.put("source", "bill");
                    txnMap.put("createdAt", Timestamp.now());

                    batch.set(txnRef, txnMap);

                    batch.commit()
                            .addOnSuccessListener(aVoid -> {
                                showSnackbar("Bill marked as paid & expense added!", "SUCCESS");
                                observeBills(); // Refresh UI
                            })
                            .addOnFailureListener(e -> {
                                showSnackbar("Failed to log payment: " + e.getLocalizedMessage(), "ERROR");
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (billsListener != null) {
            billsListener.remove();
        }
    }

    // --- Inner Calendar Adapter ---
    private class CalendarAdapter extends BaseAdapter {
        private final List<Integer> days;

        public CalendarAdapter(List<Integer> days) {
            this.days = days;
        }

        @Override
        public int getCount() {
            return days.size();
        }

        @Override
        public Object getItem(int position) {
            return days.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_calendar_cell, parent, false);
            }

            int day = days.get(position);
            TextView txtDayNumber = convertView.findViewById(R.id.txtDayNumber);
            View layoutDots = convertView.findViewById(R.id.layoutDots);
            View dotGreen = convertView.findViewById(R.id.dotGreen);
            View dotAmber = convertView.findViewById(R.id.dotAmber);
            View dotRed = convertView.findViewById(R.id.dotRed);
            View dotBlue = convertView.findViewById(R.id.dotBlue);

            if (day == 0) {
                txtDayNumber.setText("");
                layoutDots.setVisibility(View.GONE);
                convertView.setBackground(null);
            } else {
                txtDayNumber.setText(String.valueOf(day));
                layoutDots.setVisibility(View.VISIBLE);

                // Highlight selected day
                if (selectedDay == day) {
                    convertView.setBackgroundResource(R.drawable.bg_clay_button);
                    convertView.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#337C6FE0"))); // 20% primary
                    txtDayNumber.setTextColor(Color.parseColor("#7C6FE0"));
                } else {
                    convertView.setBackground(null);
                    txtDayNumber.setTextColor(Color.parseColor("#374151")); // textPrimary
                }

                // Check bills due on this day
                boolean hasPaid = false;
                boolean hasDueSoon = false;
                boolean hasOverdue = false;
                boolean hasUpcoming = false;

                for (DocumentSnapshot doc : allBills) {
                    Long dueDay = doc.getLong("dueDay");
                    if (dueDay != null && dueDay.intValue() == day) {
                        String status = getBillStatus(doc, day);
                        switch (status) {
                            case "PAID":
                                hasPaid = true;
                                break;
                            case "OVERDUE":
                                hasOverdue = true;
                                break;
                            case "DUE_SOON":
                                hasDueSoon = true;
                                break;
                            default:
                                hasUpcoming = true;
                                break;
                        }
                    }
                }

                dotGreen.setVisibility(hasPaid ? View.VISIBLE : View.GONE);
                dotRed.setVisibility(hasOverdue ? View.VISIBLE : View.GONE);
                dotAmber.setVisibility(hasDueSoon ? View.VISIBLE : View.GONE);
                dotBlue.setVisibility(hasUpcoming ? View.VISIBLE : View.GONE);
            }

            return convertView;
        }
    }

    // --- Inner Recycler List Adapter ---
    private class BillListAdapter extends RecyclerView.Adapter<BillListAdapter.BillViewHolder> {

        @NonNull
        @Override
        public BillViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_calendar_bill, parent, false);
            return new BillViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull BillViewHolder holder, int position) {
            DocumentSnapshot doc = filteredBills.get(position);
            holder.bind(doc);
        }

        @Override
        public int getItemCount() {
            return filteredBills.size();
        }

        class BillViewHolder extends RecyclerView.ViewHolder {
            private final TextView txtName;
            private final TextView txtCategory;
            private final TextView txtDueDateInfo;
            private final TextView txtAmount;
            private final TextView txtStatusBadge;
            private final View btnMarkPaid;

            public BillViewHolder(@NonNull View itemView) {
                super(itemView);
                txtName = itemView.findViewById(R.id.txtBillName);
                txtCategory = itemView.findViewById(R.id.txtBillCategory);
                txtDueDateInfo = itemView.findViewById(R.id.txtDueDateInfo);
                txtAmount = itemView.findViewById(R.id.txtBillAmount);
                txtStatusBadge = itemView.findViewById(R.id.txtStatusBadge);
                btnMarkPaid = itemView.findViewById(R.id.btnMarkPaid);
            }

            public void bind(DocumentSnapshot doc) {
                String name = doc.getString("name");
                Double amount = doc.getDouble("amount");
                String category = doc.getString("category");
                Long dueDay = doc.getLong("dueDay");

                txtName.setText(name != null ? name : "Bill");
                txtCategory.setText(category != null ? category : "Bills");
                txtAmount.setText(CurrencyManager.getInstance().formatAmount(amount != null ? amount : 0.0));

                int day = dueDay != null ? dueDay.intValue() : 1;
                txtDueDateInfo.setText("Due on day " + day);

                String status = getBillStatus(doc, day);

                // Set Badge Style
                switch (status) {
                    case "PAID":
                        txtStatusBadge.setText("PAID");
                        txtStatusBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#5DCAA5"))); // Secondary green
                        btnMarkPaid.setVisibility(View.GONE);
                        break;
                    case "OVERDUE":
                        txtStatusBadge.setText("OVERDUE");
                        txtStatusBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#E24B4A"))); // Danger red
                        btnMarkPaid.setVisibility(View.VISIBLE);
                        break;
                    case "DUE_SOON":
                        txtStatusBadge.setText("DUE SOON");
                        txtStatusBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#EF9F27"))); // Amber
                        btnMarkPaid.setVisibility(View.VISIBLE);
                        break;
                    default:
                        txtStatusBadge.setText("UPCOMING");
                        txtStatusBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#3B82F6"))); // Blue
                        btnMarkPaid.setVisibility(View.VISIBLE);
                        break;
                }

                btnMarkPaid.setOnClickListener(v -> markBillAsPaid(doc));
            }
        }
    }
}
