package com.example.bachatkhata;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.bachatkhata.databinding.ActivityGroupDetailBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GroupDetailActivity extends BaseActivity {

    private ActivityGroupDetailBinding binding;
    private FirebaseFirestore mFirestore;
    private FirebaseAuth mAuth;
    private ListenerRegistration groupListener;
    private ListenerRegistration transactionsListener;
    private ListenerRegistration claimsListener;

    private String groupId;
    private String groupName = "Group Wallet";
    private String groupType = "Shared";
    private boolean isAdmin = false;

    private TransactionAdapter transactionAdapter;
    private final List<Transaction> groupTransactionsList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGroupDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mFirestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        groupId = getIntent().getStringExtra("groupId");
        if (groupId == null) {
            Toast.makeText(this, "Group ID is missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupUI();
        observeGroupDetails();
        observeGroupTransactions();
    }

    private void setupUI() {
        binding.btnBack.setOnClickListener(v -> finish());

        binding.layoutInviteCode.setOnClickListener(v -> {
            String inviteCode = binding.txtInviteCode.getText().toString();
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Group Invite Code", inviteCode);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                showSnackbar("Invite code copied: " + inviteCode, "SUCCESS");
            }
        });

        // Add Transaction Trigger
        binding.btnAddGroupExpense.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddTransactionActivity.class);
            intent.putExtra("groupId", groupId);
            intent.putExtra("groupName", groupName);
            startActivity(intent);
        });

        // Shared transactions list
        binding.rvGroupTransactions.setLayoutManager(new LinearLayoutManager(this));
        transactionAdapter = new TransactionAdapter(transaction -> {
            // View details or open details
            Intent intent = new Intent(this, TransactionDetailActivity.class);
            intent.putExtra("transaction", transaction);
            startActivity(intent);
        });
        binding.rvGroupTransactions.setAdapter(transactionAdapter);

        // Claims list click
        binding.cardExpenseClaims.setOnClickListener(v -> {
            Intent intent = new Intent(this, OrgExpenseClaimsActivity.class);
            intent.putExtra("groupId", groupId);
            intent.putExtra("isAdmin", isAdmin);
            startActivity(intent);
        });

        // Event Fund click
        binding.cardEventFund.setOnClickListener(v -> {
            Intent intent = new Intent(this, EventFundActivity.class);
            intent.putExtra("groupId", groupId);
            startActivity(intent);
        });

        // Org Donation Tracker click
        binding.cardOrgDonation.setOnClickListener(v -> {
            Intent intent = new Intent(this, OrgDonationTrackerActivity.class);
            intent.putExtra("groupId", groupId);
            startActivity(intent);
        });
    }

    private void observeGroupDetails() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        groupListener = mFirestore.collection("groups").document(groupId)
                .addSnapshotListener((doc, error) -> {
                    if (error != null || binding == null) return;

                    if (doc != null && doc.exists()) {
                        groupName = doc.getString("name");
                        groupType = doc.getString("type");
                        Double balance = doc.getDouble("balance");
                        String inviteCode = doc.getString("inviteCode");
                        String createdBy = doc.getString("createdBy");

                        binding.txtGroupDetailTitle.setText(groupName);
                        binding.txtInviteCode.setText(inviteCode);
                        binding.txtGroupTypeBadge.setText(groupType);

                        double finalBalance = balance != null ? balance : 0.0;
                        binding.txtSharedBalance.setText(CurrencyManager.getInstance().formatAmount(finalBalance));

                        isAdmin = uid.equals(createdBy);

                        // Display conditional cards based on group type
                        if ("Organisation".equalsIgnoreCase(groupType)) {
                            binding.cardExpenseClaims.setVisibility(View.VISIBLE);
                            binding.cardOrgDonation.setVisibility(View.VISIBLE);
                            binding.cardEventFund.setVisibility(View.GONE);
                            observePendingClaims();
                        } else if ("Event Fund".equalsIgnoreCase(groupType)) {
                            binding.cardExpenseClaims.setVisibility(View.GONE);
                            binding.cardOrgDonation.setVisibility(View.GONE);
                            binding.cardEventFund.setVisibility(View.VISIBLE);
                        } else {
                            binding.cardExpenseClaims.setVisibility(View.GONE);
                            binding.cardOrgDonation.setVisibility(View.GONE);
                            binding.cardEventFund.setVisibility(View.GONE);
                        }

                        // Display members list
                        List<Map<String, Object>> members = (List<Map<String, Object>>) doc.get("members");
                        Map<String, Object> memberLimits = (Map<String, Object>) doc.get("memberLimits");
                        if (memberLimits == null) memberLimits = new HashMap<>();

                        populateMembersListUI(members, memberLimits);
                    }
                });
    }

    private void observePendingClaims() {
        if (claimsListener != null) claimsListener.remove();

        claimsListener = mFirestore.collection("groups").document(groupId)
                .collection("expense_claims")
                .whereEqualTo("status", "pending")
                .addSnapshotListener((value, error) -> {
                    if (error != null || binding == null) return;

                    if (value != null && !value.isEmpty()) {
                        int pendingCount = value.size();
                        binding.txtPendingClaimsBadge.setText(pendingCount + " pending");
                        binding.txtPendingClaimsBadge.setVisibility(View.VISIBLE);
                    } else {
                        binding.txtPendingClaimsBadge.setVisibility(View.GONE);
                    }
                });
    }

    private void observeGroupTransactions() {
        transactionsListener = mFirestore.collection("groups").document(groupId)
                .collection("group_transactions")
                .orderBy("date", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null || binding == null) return;

                    groupTransactionsList.clear();
                    if (value != null) {
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            Transaction t = Transaction.fromDocument(doc);
                            groupTransactionsList.add(t);
                        }
                    }

                    transactionAdapter.submitList(new ArrayList<>(groupTransactionsList));

                    if (groupTransactionsList.isEmpty()) {
                        binding.txtNoTransactions.setVisibility(View.VISIBLE);
                        binding.rvGroupTransactions.setVisibility(View.GONE);
                    } else {
                        binding.txtNoTransactions.setVisibility(View.GONE);
                        binding.rvGroupTransactions.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void populateMembersListUI(List<Map<String, Object>> members, Map<String, Object> memberLimits) {
        binding.layoutMembers.removeAllViews();
        if (members == null) return;

        for (Map<String, Object> member : members) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_category_manage, binding.layoutMembers, false);

            TextView txtName = row.findViewById(R.id.txtCategoryName);
            TextView txtDetails = row.findViewById(R.id.txtCategoryType);
            ImageView btnAction = row.findViewById(R.id.btnDeleteCategory);

            String name = (String) member.get("name");
            String role = (String) member.get("role");
            String memberUid = (String) member.get("uid");

            txtName.setText(name);

            // Fetch member limit
            Double limit = null;
            if (memberUid != null && memberLimits.containsKey(memberUid)) {
                Object limitVal = memberLimits.get(memberUid);
                if (limitVal instanceof Number) {
                    limit = ((Number) limitVal).doubleValue();
                }
            }

            String detailsText = role.substring(0, 1).toUpperCase(Locale.US) + role.substring(1);
            if (limit != null) {
                detailsText += " • Limit: " + CurrencyManager.getInstance().formatAmount(limit);
            }
            txtDetails.setText(detailsText);
            txtDetails.setVisibility(View.VISIBLE);

            // Change delete icon to "limit setting / config icon" if admin, or hide it if member
            if (isAdmin && memberUid != null && !memberUid.isEmpty() && !memberUid.equals(mAuth.getCurrentUser().getUid())) {
                btnAction.setImageResource(R.drawable.ic_wallet); // Set limit icon
                btnAction.setContentDescription("Set Spending Limit");
                btnAction.setVisibility(View.VISIBLE);
                final Double finalLimit = limit;
                btnAction.setOnClickListener(v -> showSetLimitDialog(memberUid, name, finalLimit));
            } else {
                btnAction.setVisibility(View.GONE);
            }

            binding.layoutMembers.addView(row);
        }
    }

    private void showSetLimitDialog(String memberUid, String name, Double currentLimit) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Set Spending Limit");
        builder.setMessage("Configure monthly spending limit for " + name);

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (currentLimit != null) {
            input.setText(String.format(Locale.US, "%.2f", currentLimit));
        }
        input.setHint("Enter amount (e.g. 5000)");

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        input.setLayoutParams(lp);
        builder.setView(input);

        builder.setPositiveButton("Save Limit", (dialog, which) -> {
            String valueStr = input.getText().toString().trim();
            if (valueStr.isEmpty()) {
                removeMemberLimit(memberUid);
                return;
            }

            try {
                double limit = Double.parseDouble(valueStr);
                saveMemberLimit(memberUid, limit);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid limit amount", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Remove Limit", (dialog, which) -> removeMemberLimit(memberUid));
        builder.setNeutralButton("Cancel", null);
        builder.show();
    }

    private void saveMemberLimit(String memberUid, double limit) {
        DocumentReference groupRef = mFirestore.collection("groups").document(groupId);
        mFirestore.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(groupRef);
            Map<String, Object> memberLimits = (Map<String, Object>) snapshot.get("memberLimits");
            if (memberLimits == null) {
                memberLimits = new HashMap<>();
            }
            memberLimits.put(memberUid, limit);
            transaction.update(groupRef, "memberLimits", memberLimits);
            return null;
        }).addOnSuccessListener(aVoid -> showSnackbar("Spending limit updated!", "SUCCESS"))
          .addOnFailureListener(e -> Toast.makeText(this, "Failed to update limit: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show());
    }

    private void removeMemberLimit(String memberUid) {
        DocumentReference groupRef = mFirestore.collection("groups").document(groupId);
        mFirestore.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(groupRef);
            Map<String, Object> memberLimits = (Map<String, Object>) snapshot.get("memberLimits");
            if (memberLimits != null) {
                memberLimits.remove(memberUid);
                transaction.update(groupRef, "memberLimits", memberLimits);
            }
            return null;
        }).addOnSuccessListener(aVoid -> showSnackbar("Spending limit removed!", "SUCCESS"))
          .addOnFailureListener(e -> Toast.makeText(this, "Failed to remove limit: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (groupListener != null) groupListener.remove();
        if (transactionsListener != null) transactionsListener.remove();
        if (claimsListener != null) claimsListener.remove();
    }
}
