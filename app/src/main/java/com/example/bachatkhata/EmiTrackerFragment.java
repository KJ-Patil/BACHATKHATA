package com.example.bachatkhata;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bachatkhata.databinding.FragmentEmiTrackerBinding;
import com.example.bachatkhata.databinding.ItemEmiBinding;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class EmiTrackerFragment extends Fragment {

    private FragmentEmiTrackerBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;
    private EmiAdapter adapter;
    private final List<DocumentSnapshot> emiList = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.US);
    private ListenerRegistration emiListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentEmiTrackerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();

        binding.btnBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        setupRecyclerView();

        if (mAuth.getCurrentUser() != null) {
            observeEmis(mAuth.getCurrentUser().getUid());
        }

        binding.fabAddEmi.setOnClickListener(v ->
                new AddLoanDialog().show(getChildFragmentManager(), "AddLoan"));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mAuth.getCurrentUser() != null) {
            observeEmis(mAuth.getCurrentUser().getUid());
        }
    }

    private void setupRecyclerView() {
        adapter = new EmiAdapter();
        binding.rvEmis.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvEmis.setAdapter(adapter);
    }

    private void observeEmis(String uid) {
        // Both onViewCreated() and onResume() call this. Detach any existing
        // registration first so listeners don't stack up on every resume.
        if (emiListener != null) {
            emiListener.remove();
            emiListener = null;
        }
        emiListener = mFirestore.collection("users").document(uid).collection("emis")
                .addSnapshotListener((value, error) -> {
                    if (error != null || binding == null) return;

                    emiList.clear();
                    double totalMonthlyBurden = 0.0;

                    if (value != null) {
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            emiList.add(doc);
                            Double emiVal = doc.getDouble("emiAmount");
                            if (emiVal != null) {
                                totalMonthlyBurden += emiVal;
                            }
                        }
                    }

                    // Sort list: calculate next due date dynamically and sort ascending
                    Collections.sort(emiList, (doc1, doc2) -> {
                        Date d1 = calculateNextDueDate(getDateFromObject(doc1.get("startDate")));
                        Date d2 = calculateNextDueDate(getDateFromObject(doc2.get("startDate")));
                        if (d1 == null || d2 == null) return 0;
                        return d1.compareTo(d2);
                    });

                    binding.txtEmiSummaryTitle.setText("Total Monthly EMI Burden: " + 
                            CurrencyManager.getInstance().formatAmount(totalMonthlyBurden));
                    binding.txtEmiCountSubtitle.setText(String.format(Locale.US, "Active across %d loans", emiList.size()));
                    adapter.notifyDataSetChanged();

                    if (emiList.isEmpty()) {
                        binding.layoutEmptyState.setVisibility(View.VISIBLE);
                        binding.rvEmis.setVisibility(View.GONE);
                    } else {
                        binding.layoutEmptyState.setVisibility(View.GONE);
                        binding.rvEmis.setVisibility(View.VISIBLE);
                    }
                });
    }

    private Date getDateFromObject(Object obj) {
        if (obj instanceof Timestamp) {
            return ((Timestamp) obj).toDate();
        } else if (obj instanceof Date) {
            return (Date) obj;
        }
        return null;
    }

    private Date calculateNextDueDate(Date startDate) {
        if (startDate == null) return null;
        Calendar start = Calendar.getInstance();
        start.setTime(startDate);
        Calendar now = Calendar.getInstance();
        
        Calendar due = Calendar.getInstance();
        due.set(Calendar.DAY_OF_MONTH, start.get(Calendar.DAY_OF_MONTH));
        due.set(Calendar.HOUR_OF_DAY, 8);
        due.set(Calendar.MINUTE, 0);
        due.set(Calendar.SECOND, 0);
        due.set(Calendar.MILLISECOND, 0);

        if (due.before(now)) {
            due.add(Calendar.MONTH, 1);
        }
        return due.getTime();
    }

    // RecyclerView Adapter
    private class EmiAdapter extends RecyclerView.Adapter<EmiViewHolder> {

        @NonNull
        @Override
        public EmiViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemEmiBinding itemBinding = ItemEmiBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new EmiViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull EmiViewHolder holder, int position) {
            DocumentSnapshot doc = emiList.get(position);
            String name = doc.getString("loanName");
            String type = doc.getString("loanType");
            Double principal = doc.getDouble("principal");
            Double rate = doc.getDouble("interestRate");
            Long tenureVal = doc.getLong("tenureMonths");
            Double emiAmount = doc.getDouble("emiAmount");
            Date startDate = getDateFromObject(doc.get("startDate"));

            int tenure = tenureVal != null ? tenureVal.intValue() : 0;
            double p = principal != null ? principal : 0.0;
            double r = rate != null ? rate : 0.0;
            double emi = emiAmount != null ? emiAmount : 0.0;

            holder.binding.txtLoanName.setText(name != null ? name : "Loan");
            holder.binding.txtLoanType.setText(type != null ? type : "Personal");
            holder.binding.txtInterestInfo.setText(String.format(Locale.US, "%.1f%% p.a.", r));
            holder.binding.txtEmiAmount.setText(CurrencyManager.getInstance().formatAmount(emi));

            // Use stored monthsPaid when available; fall back to date-based calculation
            int paidMonths = 0;
            Long storedMonthsPaid = doc.getLong("monthsPaid");
            if (storedMonthsPaid != null) {
                paidMonths = storedMonthsPaid.intValue();
                if (paidMonths < 0) paidMonths = 0;
                if (paidMonths > tenure) paidMonths = tenure;
            } else if (startDate != null) {
                Calendar start = Calendar.getInstance();
                start.setTime(startDate);
                Calendar now = Calendar.getInstance();

                int diffYear = now.get(Calendar.YEAR) - start.get(Calendar.YEAR);
                int diffMonth = now.get(Calendar.MONTH) - start.get(Calendar.MONTH);
                paidMonths = diffYear * 12 + diffMonth;

                if (paidMonths < 0) paidMonths = 0;
                if (paidMonths > tenure) paidMonths = tenure;
            }

            holder.binding.txtTenureProgress.setText(String.format(Locale.US, "%d / %d months paid", paidMonths, tenure));
            
            // Calculate progress percentage
            int progress = tenure > 0 ? (paidMonths * 100) / tenure : 0;
            holder.binding.progressTenure.setProgress(progress);

            // Calculate outstanding principal balance
            double outstanding = p - (emi * paidMonths);
            if (outstanding < 0) outstanding = 0.0;
            holder.binding.txtOutstanding.setText(CurrencyManager.getInstance().formatAmount(outstanding));

            // Set Next Due Date and remaining days badge
            Date nextDue = calculateNextDueDate(startDate);
            if (nextDue != null) {
                holder.binding.txtDueDate.setText("Next due: " + dateFormat.format(nextDue));

                long diffMs = nextDue.getTime() - new Date().getTime();
                long daysLeft = TimeUnit.MILLISECONDS.toDays(diffMs);
                if (daysLeft < 0) daysLeft = 0;

                holder.binding.txtEmiDaysBadge.setText(daysLeft == 0 ? "Due Today" : daysLeft + " days left");
                
                if (daysLeft <= 3) {
                    holder.binding.txtEmiDaysBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E24B4A"))); // danger red
                } else if (daysLeft <= 7) {
                    holder.binding.txtEmiDaysBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#EF9F27"))); // warning amber
                } else {
                    holder.binding.txtEmiDaysBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#5DCAA5"))); // secondary green
                }
            } else {
                holder.binding.txtDueDate.setText("Next due: -");
                holder.binding.txtEmiDaysBadge.setText("Active");
                holder.binding.txtEmiDaysBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#5DCAA5")));
            }

            holder.binding.btnDeleteEmi.setOnClickListener(v -> {
                new AlertDialog.Builder(v.getContext())
                        .setTitle("Delete EMI")
                        .setMessage("Are you sure you want to delete this EMI?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            if (mAuth.getCurrentUser() != null) {
                                String emiId = doc.getId();
                                mFirestore.collection("users")
                                        .document(mAuth.getCurrentUser().getUid())
                                        .collection("emis")
                                        .document(emiId)
                                        .delete()
                                        .addOnSuccessListener(aVoid -> {
                                            androidx.work.WorkManager.getInstance(v.getContext())
                                                    .cancelAllWorkByTag("emi_" + emiId);
                                            Toast.makeText(v.getContext(), "EMI deleted", Toast.LENGTH_SHORT).show();
                                        })
                                        .addOnFailureListener(e -> Toast.makeText(v.getContext(), "Failed to delete: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        @Override
        public int getItemCount() {
            return emiList.size();
        }
    }

    private static class EmiViewHolder extends RecyclerView.ViewHolder {
        final ItemEmiBinding binding;

        EmiViewHolder(ItemEmiBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (emiListener != null) {
            emiListener.remove();
            emiListener = null;
        }
        binding = null;
    }
}
