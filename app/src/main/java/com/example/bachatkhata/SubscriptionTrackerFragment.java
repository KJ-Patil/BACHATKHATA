package com.example.bachatkhata;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bachatkhata.databinding.FragmentSubscriptionTrackerBinding;
import com.example.bachatkhata.databinding.ItemSubscriptionBinding;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SubscriptionTrackerFragment extends Fragment {

    private FragmentSubscriptionTrackerBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;
    private SubscriptionAdapter adapter;
    private final List<Map<String, Object>> subscriptionsList = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.US);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSubscriptionTrackerBinding.inflate(inflater, container, false);
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
            String uid = mAuth.getCurrentUser().getUid();
            
            // 1. Trigger Auto-Detection from user transaction history
            triggerAutoDetection(uid);

            // 2. Observe Active Subscriptions in real time
            observeSubscriptions(uid);
        }

        binding.fabAddSubscription.setOnClickListener(v -> {
            AddSubscriptionBottomSheet sheet = new AddSubscriptionBottomSheet();
            sheet.setOnDismissCallback(() -> {
                if (mAuth.getCurrentUser() != null) {
                    observeSubscriptions(mAuth.getCurrentUser().getUid());
                }
            });
            sheet.show(getChildFragmentManager(), "AddSubscriptionBottomSheet");
        });
    }

    private void setupRecyclerView() {
        adapter = new SubscriptionAdapter();
        binding.rvSubscriptions.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvSubscriptions.setAdapter(adapter);
    }

    private void triggerAutoDetection(String uid) {
        // Load transaction list from database to auto-detect recurring bills
        mFirestore.collection("users").document(uid).collection("transactions")
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Transaction> list = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        list.add(Transaction.fromDocument(doc));
                    }
                    SubscriptionDetector.detectFromTransactions(uid, list);
                });
    }

    private void observeSubscriptions(String uid) {
        mFirestore.collection("users").document(uid).collection("subscriptions")
                .whereEqualTo("isActive", true)
                .addSnapshotListener((value, error) -> {
                    if (error != null || binding == null) return;

                    subscriptionsList.clear();
                    double totalBurden = 0.0;

                    if (value != null) {
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            Map<String, Object> data = doc.getData();
                            if (data != null) {
                                subscriptionsList.add(data);
                                Double amt = doc.getDouble("amount");
                                if (amt != null) {
                                    totalBurden += amt;
                                }
                            }
                        }
                    }

                    // Sort list by next renewal date ascending
                    Collections.sort(subscriptionsList, (o1, o2) -> {
                        Date d1 = getDateFromObject(o1.get("nextRenewalDate"));
                        Date d2 = getDateFromObject(o2.get("nextRenewalDate"));
                        if (d1 == null || d2 == null) return 0;
                        return d1.compareTo(d2);
                    });

                    binding.txtTotalSubscriptionBurden.setText(CurrencyManager.getInstance().formatAmount(totalBurden));
                    adapter.notifyDataSetChanged();

                    if (subscriptionsList.isEmpty()) {
                        binding.layoutEmptyState.setVisibility(View.VISIBLE);
                        binding.rvSubscriptions.setVisibility(View.GONE);
                    } else {
                        binding.layoutEmptyState.setVisibility(View.GONE);
                        binding.rvSubscriptions.setVisibility(View.VISIBLE);
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

    // RecyclerView Adapter class
    private class SubscriptionAdapter extends RecyclerView.Adapter<SubscriptionViewHolder> {

        @NonNull
        @Override
        public SubscriptionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemSubscriptionBinding itemBinding = ItemSubscriptionBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new SubscriptionViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull SubscriptionViewHolder holder, int position) {
            Map<String, Object> data = subscriptionsList.get(position);
            String name = (String) data.get("name");
            Double amount = (Double) data.get("amount");
            Object dateObj = data.get("nextRenewalDate");
            Date nextRenewal = getDateFromObject(dateObj);

            holder.binding.txtSubscriptionName.setText(name != null ? name : "Subscription");
            holder.binding.txtSubscriptionAmount.setText(amount != null ? CurrencyManager.getInstance().formatAmount(amount) : "₹0.00");

            if (nextRenewal != null) {
                holder.binding.txtRenewalInfo.setText("Renews on " + dateFormat.format(nextRenewal));

                // Proximity calculations
                long diffMs = nextRenewal.getTime() - new Date().getTime();
                long daysAway = TimeUnit.MILLISECONDS.toDays(diffMs);

                // Set badge layout style colors
                if (daysAway < 0) {
                    holder.binding.txtDaysBadge.setText("Overdue");
                    holder.binding.txtDaysBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E24B4A"))); // colorDanger
                } else if (daysAway <= 3) {
                    holder.binding.txtDaysBadge.setText(daysAway == 0 ? "Renews Today" : "Due in " + daysAway + " days");
                    holder.binding.txtDaysBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E24B4A"))); // colorDanger
                } else if (daysAway <= 7) {
                    holder.binding.txtDaysBadge.setText("Due in " + daysAway + " days");
                    holder.binding.txtDaysBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#EF9F27"))); // colorWarning
                } else {
                    holder.binding.txtDaysBadge.setText(daysAway + " days away");
                    holder.binding.txtDaysBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#5DCAA5"))); // colorSecondary
                }
            } else {
                holder.binding.txtRenewalInfo.setText("Manual Subscription");
                holder.binding.txtDaysBadge.setText("Active");
                holder.binding.txtDaysBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#5DCAA5")));
            }
        }

        @Override
        public int getItemCount() {
            return subscriptionsList.size();
        }
    }

    private static class SubscriptionViewHolder extends RecyclerView.ViewHolder {
        final ItemSubscriptionBinding binding;

        SubscriptionViewHolder(ItemSubscriptionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
