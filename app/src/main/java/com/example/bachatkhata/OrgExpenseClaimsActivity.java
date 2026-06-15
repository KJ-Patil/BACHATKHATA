package com.example.bachatkhata;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.bachatkhata.databinding.ActivityOrgExpenseClaimsBinding;
import com.example.bachatkhata.databinding.ItemExpenseClaimBinding;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OrgExpenseClaimsActivity extends BaseActivity {

    private ActivityOrgExpenseClaimsBinding binding;
    private FirebaseFirestore mFirestore;
    private FirebaseAuth mAuth;
    private ListenerRegistration claimsListener;

    private String groupId;
    private boolean isAdmin = false;
    private String selectedStatus = "pending";

    private final List<DocumentSnapshot> claimsList = new ArrayList<>();
    private ClaimsAdapter adapter;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOrgExpenseClaimsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mFirestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        groupId = getIntent().getStringExtra("groupId");
        isAdmin = getIntent().getBooleanExtra("isAdmin", false);

        if (groupId == null) {
            Toast.makeText(this, "Group ID is missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupUI();
        observeClaims();
    }

    private void setupUI() {
        binding.btnBack.setOnClickListener(v -> finish());

        // Setup Tabs
        binding.tabLayoutClaims.addTab(binding.tabLayoutClaims.newTab().setText("Pending"));
        binding.tabLayoutClaims.addTab(binding.tabLayoutClaims.newTab().setText("Approved"));
        binding.tabLayoutClaims.addTab(binding.tabLayoutClaims.newTab().setText("Rejected"));

        binding.tabLayoutClaims.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                if (position == 0) {
                    selectedStatus = "pending";
                } else if (position == 1) {
                    selectedStatus = "approved";
                } else {
                    selectedStatus = "rejected";
                }
                observeClaims();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        // Setup FAB visibility based on role
        if (isAdmin) {
            binding.fabSubmitClaim.setVisibility(View.GONE);
        } else {
            binding.fabSubmitClaim.setVisibility(View.VISIBLE);
            binding.fabSubmitClaim.setOnClickListener(v -> {
                SubmitClaimBottomSheet sheet = SubmitClaimBottomSheet.newInstance(groupId);
                sheet.setOnDismissCallback(this::observeClaims);
                sheet.show(getSupportFragmentManager(), "SubmitClaimBottomSheet");
            });
        }

        // Setup Recycler
        binding.rvClaims.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ClaimsAdapter();
        binding.rvClaims.setAdapter(adapter);
    }

    private void observeClaims() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        if (claimsListener != null) {
            claimsListener.remove();
        }

        Query query = mFirestore.collection("groups").document(groupId)
                .collection("expense_claims")
                .whereEqualTo("status", selectedStatus);

        // Members can only view their own claims
        if (!isAdmin) {
            query = query.whereEqualTo("submitterUid", uid);
        }

        query = query.orderBy("createdAt", Query.Direction.DESCENDING);

        claimsListener = query.addSnapshotListener((value, error) -> {
            if (error != null || binding == null) {
                return;
            }

            claimsList.clear();
            if (value != null) {
                claimsList.addAll(value.getDocuments());
            }

            adapter.notifyDataSetChanged();
            updateEmptyState();
        });
    }

    private void updateEmptyState() {
        if (claimsList.isEmpty()) {
            binding.rvClaims.setVisibility(View.GONE);
            binding.layoutEmptyState.setVisibility(View.VISIBLE);
        } else {
            binding.rvClaims.setVisibility(View.VISIBLE);
            binding.layoutEmptyState.setVisibility(View.GONE);
        }
    }

    private void approveClaim(DocumentSnapshot claimDoc) {
        if (mAuth.getCurrentUser() == null) return;
        String adminUid = mAuth.getCurrentUser().getUid();

        String claimId = claimDoc.getString("id");
        String submitterUid = claimDoc.getString("submitterUid");
        String submitterName = claimDoc.getString("submitterName");
        Double amount = claimDoc.getDouble("amount");
        String category = claimDoc.getString("category");
        String note = claimDoc.getString("note");
        String receiptUrl = claimDoc.getString("receiptUrl");

        double finalAmount = amount != null ? amount : 0.0;
        String finalCategory = category != null ? category : "Bills";

        WriteBatch batch = mFirestore.batch();

        // 1. Update claim status to approved
        DocumentReference claimRef = mFirestore.collection("groups").document(groupId)
                .collection("expense_claims").document(claimId);
        batch.update(claimRef,
                "status", "approved",
                "reviewedBy", adminUid,
                "reviewedAt", Timestamp.now());

        // 2. Add transaction to group ledger
        DocumentReference groupTxnRef = mFirestore.collection("groups").document(groupId)
                .collection("group_transactions").document();
        Map<String, Object> txnMap = new HashMap<>();
        txnMap.put("id", groupTxnRef.getId());
        txnMap.put("amount", finalAmount);
        txnMap.put("type", "expense");
        txnMap.put("category", finalCategory);
        txnMap.put("note", "Claim: " + note + " (Approved)");
        txnMap.put("date", new Timestamp(new Date()));
        txnMap.put("account", "Bank");
        txnMap.put("currency", CurrencyManager.getInstance().getCurrentCurrencyCode());
        txnMap.put("currencySymbol", CurrencyManager.getInstance().getCurrentCurrencySymbol());
        txnMap.put("source", "claim");
        txnMap.put("receiptUrl", receiptUrl);
        txnMap.put("createdAt", Timestamp.now());

        batch.set(groupTxnRef, txnMap);

        // 3. Add transaction to claimant's personal transactions collection
        DocumentReference userTxnRef = mFirestore.collection("users").document(submitterUid)
                .collection("transactions").document();
        Map<String, Object> userTxnMap = new HashMap<>(txnMap);
        userTxnMap.put("id", userTxnRef.getId());
        userTxnMap.put("note", "Shared Claim: " + note);
        batch.set(userTxnRef, userTxnMap);

        // 4. Update group balance atomically
        DocumentReference groupRef = mFirestore.collection("groups").document(groupId);
        batch.update(groupRef, "balance", com.google.firebase.firestore.FieldValue.increment(-finalAmount));

        batch.commit()
                .addOnSuccessListener(aVoid -> showSnackbar("Claim Approved!", "SUCCESS"))
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to approve: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show());
    }

    private void rejectClaim(DocumentSnapshot claimDoc) {
        if (mAuth.getCurrentUser() == null) return;
        String adminUid = mAuth.getCurrentUser().getUid();

        String claimId = claimDoc.getString("id");

        mFirestore.collection("groups").document(groupId)
                .collection("expense_claims").document(claimId)
                .update(
                        "status", "rejected",
                        "reviewedBy", adminUid,
                        "reviewedAt", Timestamp.now()
                )
                .addOnSuccessListener(aVoid -> showSnackbar("Claim Rejected", "ERROR"))
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to reject: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (claimsListener != null) {
            claimsListener.remove();
        }
    }

    // --- Inner Adapter for Claims ---
    private class ClaimsAdapter extends RecyclerView.Adapter<ClaimsAdapter.ClaimViewHolder> {

        @NonNull
        @Override
        public ClaimViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemExpenseClaimBinding itemBinding = ItemExpenseClaimBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new ClaimViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull ClaimViewHolder holder, int position) {
            DocumentSnapshot doc = claimsList.get(position);
            holder.bind(doc);
        }

        @Override
        public int getItemCount() {
            return claimsList.size();
        }

        class ClaimViewHolder extends RecyclerView.ViewHolder {
            private final ItemExpenseClaimBinding itemBinding;

            public ClaimViewHolder(@NonNull ItemExpenseClaimBinding itemBinding) {
                super(itemBinding.getRoot());
                this.itemBinding = itemBinding;
            }

            public void bind(DocumentSnapshot doc) {
                String submitterName = doc.getString("submitterName");
                Double amount = doc.getDouble("amount");
                String category = doc.getString("category");
                String note = doc.getString("note");
                String status = doc.getString("status");
                String receiptUrl = doc.getString("receiptUrl");
                Timestamp createdAt = doc.getTimestamp("createdAt");

                itemBinding.txtClaimSubmitter.setText(submitterName != null ? submitterName : "Member");
                itemBinding.txtClaimNote.setText(note);

                double finalAmount = amount != null ? amount : 0.0;
                itemBinding.txtClaimAmount.setText(CurrencyManager.getInstance().formatAmount(finalAmount));

                String dateStr = createdAt != null ? dateFormat.format(createdAt.toDate()) : "";
                itemBinding.txtClaimCategory.setText((category != null ? category : "Bills") + " • " + dateStr);

                // Setup receipt preview if available
                if (receiptUrl != null && !receiptUrl.trim().isEmpty()) {
                    itemBinding.imgReceiptPreview.setVisibility(View.VISIBLE);
                    Glide.with(itemView.getContext())
                            .load(receiptUrl)
                            .placeholder(R.drawable.ic_placeholder)
                            .into(itemBinding.imgReceiptPreview);
                } else {
                    itemBinding.imgReceiptPreview.setVisibility(View.GONE);
                }

                // Setup Status Badges and Admin Controls
                if ("pending".equalsIgnoreCase(status)) {
                    itemBinding.txtClaimStatusBadge.setVisibility(View.GONE);

                    if (isAdmin) {
                        itemBinding.dividerAction.setVisibility(View.VISIBLE);
                        itemBinding.layoutAdminActions.setVisibility(View.VISIBLE);

                        itemBinding.btnApproveClaim.setOnClickListener(v -> approveClaim(doc));
                        itemBinding.btnRejectClaim.setOnClickListener(v -> rejectClaim(doc));
                    } else {
                        itemBinding.dividerAction.setVisibility(View.GONE);
                        itemBinding.layoutAdminActions.setVisibility(View.GONE);
                    }
                } else {
                    itemBinding.dividerAction.setVisibility(View.VISIBLE);
                    itemBinding.layoutAdminActions.setVisibility(View.GONE);
                    itemBinding.txtClaimStatusBadge.setVisibility(View.VISIBLE);

                    if ("approved".equalsIgnoreCase(status)) {
                        itemBinding.txtClaimStatusBadge.setText("APPROVED");
                        itemBinding.txtClaimStatusBadge.setTextColor(Color.parseColor("#5DCAA5"));
                        itemBinding.txtClaimStatusBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#1A5DCAA5")));
                    } else {
                        itemBinding.txtClaimStatusBadge.setText("REJECTED");
                        itemBinding.txtClaimStatusBadge.setTextColor(Color.parseColor("#E24B4A"));
                        itemBinding.txtClaimStatusBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#1AE24B4A")));
                    }
                }
            }
        }
    }
}
