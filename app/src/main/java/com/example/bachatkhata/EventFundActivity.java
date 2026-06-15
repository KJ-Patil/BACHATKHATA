package com.example.bachatkhata;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.bachatkhata.databinding.ActivityEventFundBinding;
import com.google.firebase.Timestamp;
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

public class EventFundActivity extends BaseActivity {

    private ActivityEventFundBinding binding;
    private FirebaseFirestore mFirestore;
    private FirebaseAuth mAuth;

    private String groupId;
    private String currentUid;
    private String currentUserName = "You";

    private double targetBudget = 10000.0;
    private final List<Map<String, Object>> groupMembers = new ArrayList<>();
    private final List<Map<String, Object>> contributionsList = new ArrayList<>();
    private final List<Map<String, Object>> expensesList = new ArrayList<>();

    private ListenerRegistration groupListener;
    private ListenerRegistration contributionsListener;
    private ListenerRegistration expensesListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEventFundBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mFirestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        if (mAuth.getCurrentUser() == null) {
            finish();
            return;
        }

        currentUid = mAuth.getCurrentUser().getUid();

        groupId = getIntent().getStringExtra("groupId");
        if (groupId == null) {
            Toast.makeText(this, "Group ID is required", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupUI();
        observeGroupDetails();
        observeContributions();
        observeExpenses();
    }

    private void setupUI() {
        binding.btnBack.setOnClickListener(v -> finish());

        // Update target budget click
        binding.btnSaveBudget.setOnClickListener(v -> {
            String budgetStr = binding.inputTargetBudget.getText().toString().trim();
            if (budgetStr.isEmpty()) return;

            try {
                double newBudget = Double.parseDouble(budgetStr);
                mFirestore.collection("groups").document(groupId)
                        .update("eventTargetBudget", newBudget)
                        .addOnSuccessListener(aVoid -> {
                            targetBudget = newBudget;
                            showSnackbar("Target budget updated!", "SUCCESS");
                            recalculateSettlements();
                        })
                        .addOnFailureListener(e -> showSnackbar("Failed to update budget", "ERROR"));
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid budget amount", Toast.LENGTH_SHORT).show();
            }
        });

        // Add contribution click
        binding.btnAddContribution.setOnClickListener(v -> {
            String contStr = binding.inputContribution.getText().toString().trim();
            if (contStr.isEmpty()) return;

            try {
                double amt = Double.parseDouble(contStr);
                addContribution(amt);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show();
            }
        });

        // Add expense click
        binding.btnAddExpense.setOnClickListener(v -> showAddExpenseDialog());
    }

    private void observeGroupDetails() {
        groupListener = mFirestore.collection("groups").document(groupId)
                .addSnapshotListener((doc, error) -> {
                    if (error != null || binding == null) return;

                    if (doc != null && doc.exists()) {
                        String name = doc.getString("name");
                        binding.txtEventName.setText(name);

                        Double budget = doc.getDouble("eventTargetBudget");
                        if (budget != null) {
                            targetBudget = budget;
                        }
                        binding.inputTargetBudget.setText(String.format(Locale.US, "%.2f", targetBudget));

                        // Store group members
                        groupMembers.clear();
                        List<Map<String, Object>> members = (List<Map<String, Object>>) doc.get("members");
                        if (members != null) {
                            groupMembers.addAll(members);
                            for (Map<String, Object> member : members) {
                                String mUid = (String) member.get("uid");
                                if (currentUid.equals(mUid)) {
                                    currentUserName = (String) member.get("name");
                                }
                            }
                        }

                        recalculateSettlements();
                    }
                });
    }

    private void observeContributions() {
        contributionsListener = mFirestore.collection("groups").document(groupId)
                .collection("event_contributions")
                .orderBy("date", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null || binding == null) return;

                    contributionsList.clear();
                    if (value != null) {
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            Map<String, Object> data = doc.getData();
                            if (data != null) {
                                data.put("id", doc.getId());
                                contributionsList.add(data);
                            }
                        }
                    }

                    renderContributions();
                    recalculateSettlements();
                });
    }

    private void observeExpenses() {
        expensesListener = mFirestore.collection("groups").document(groupId)
                .collection("event_expenses")
                .orderBy("date", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null || binding == null) return;

                    expensesList.clear();
                    if (value != null) {
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            Map<String, Object> data = doc.getData();
                            if (data != null) {
                                data.put("id", doc.getId());
                                expensesList.add(data);
                            }
                        }
                    }

                    renderExpenses();
                    recalculateSettlements();
                });
    }

    private void addContribution(double amount) {
        showLoadingDialog();

        Map<String, Object> cont = new HashMap<>();
        cont.put("uid", currentUid);
        cont.put("name", currentUserName);
        cont.put("amount", amount);
        cont.put("date", Timestamp.now());

        mFirestore.collection("groups").document(groupId)
                .collection("event_contributions")
                .add(cont)
                .addOnSuccessListener(ref -> {
                    hideLoadingDialog();
                    binding.inputContribution.setText("");
                    showSnackbar("Contribution logged!", "SUCCESS");
                })
                .addOnFailureListener(e -> {
                    hideLoadingDialog();
                    showSnackbar("Failed to log contribution", "ERROR");
                });
    }

    private void showAddExpenseDialog() {
        if (groupMembers.isEmpty()) {
            Toast.makeText(this, "Group members list loading...", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Log Event Expense");

        // Inflate custom form layout
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 16, 24, 16);

        final EditText inputAmt = new EditText(this);
        inputAmt.setHint("Amount (₹)");
        inputAmt.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(inputAmt);

        final EditText inputNote = new EditText(this);
        inputNote.setHint("Description (e.g. Taxi, Dinner)");
        layout.addView(inputNote);

        // Category Spinner
        final Spinner spinCat = new Spinner(this);
        String[] categories = {"Food", "Transport", "Shopping", "Bills", "Other"};
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinCat.setAdapter(catAdapter);
        spinCat.setPadding(0, 16, 0, 16);
        layout.addView(spinCat);

        // Paid By Spinner
        final Spinner spinPaidBy = new Spinner(this);
        List<String> memberNames = new ArrayList<>();
        for (Map<String, Object> m : groupMembers) {
            memberNames.add((String) m.get("name"));
        }
        ArrayAdapter<String> memAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, memberNames);
        memAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinPaidBy.setAdapter(memAdapter);
        spinPaidBy.setPadding(0, 16, 0, 16);

        // Pre-select current user in spinner
        int myIndex = memberNames.indexOf(currentUserName);
        if (myIndex != -1) {
            spinPaidBy.setSelection(myIndex);
        }
        layout.addView(spinPaidBy);

        builder.setView(layout);

        builder.setPositiveButton("Log", (dialog, which) -> {
            String amtStr = inputAmt.getText().toString().trim();
            String note = inputNote.getText().toString().trim();
            String category = spinCat.getSelectedItem().toString();
            int selectedMemIndex = spinPaidBy.getSelectedItemPosition();

            if (amtStr.isEmpty() || selectedMemIndex == -1) {
                Toast.makeText(this, "Amount and Paid By are required", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                double amt = Double.parseDouble(amtStr);
                Map<String, Object> member = groupMembers.get(selectedMemIndex);
                String payUid = (String) member.get("uid");
                String payName = (String) member.get("name");

                logExpense(payUid, payName, amt, category, note);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void logExpense(String payUid, String payName, double amount, String category, String note) {
        showLoadingDialog();

        Map<String, Object> exp = new HashMap<>();
        exp.put("uid", payUid);
        exp.put("name", payName);
        exp.put("amount", amount);
        exp.put("category", category);
        exp.put("note", note.isEmpty() ? category : note);
        exp.put("date", Timestamp.now());

        mFirestore.collection("groups").document(groupId)
                .collection("event_expenses")
                .add(exp)
                .addOnSuccessListener(ref -> {
                    hideLoadingDialog();
                    showSnackbar("Expense logged successfully!", "SUCCESS");
                })
                .addOnFailureListener(e -> {
                    hideLoadingDialog();
                    showSnackbar("Failed to log expense", "ERROR");
                });
    }

    private void renderContributions() {
        binding.layoutContributionsList.removeAllViews();
        double sum = 0.0;

        for (Map<String, Object> cont : contributionsList) {
            Double amount = (Double) cont.get("amount");
            String name = (String) cont.get("name");
            String contId = (String) cont.get("id");
            String contUid = (String) cont.get("uid");

            if (amount == null) continue;
            sum += amount;

            View row = LayoutInflater.from(this).inflate(R.layout.item_category_manage, binding.layoutContributionsList, false);
            TextView txtName = row.findViewById(R.id.txtCategoryName);
            TextView txtAmount = row.findViewById(R.id.txtCategoryType);
            ImageView btnDelete = row.findViewById(R.id.btnDeleteCategory);

            txtName.setText(name);
            txtAmount.setText(CurrencyManager.getInstance().formatAmount(amount));
            txtAmount.setVisibility(View.VISIBLE);
            txtAmount.setTextColor(getResources().getColor(R.color.colorSecondary));

            // Only allow deleting self contribution
            if (currentUid.equals(contUid)) {
                btnDelete.setVisibility(View.VISIBLE);
                btnDelete.setOnClickListener(v -> deleteContribution(contId));
            } else {
                btnDelete.setVisibility(View.GONE);
            }

            binding.layoutContributionsList.addView(row);
        }

        binding.txtPoolProgress.setText(String.format(Locale.US, "₹%.2f / ₹%.2f", sum, targetBudget));
        if (targetBudget > 0) {
            int pct = (int) ((sum * 100) / targetBudget);
            binding.txtPoolProgressPercent.setText(pct + "%");
            binding.poolProgressBar.setProgress(Math.min(pct, 100));
        }
    }

    private void deleteContribution(String contId) {
        showLoadingDialog();
        mFirestore.collection("groups").document(groupId)
                .collection("event_contributions").document(contId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    hideLoadingDialog();
                    showSnackbar("Contribution removed", "SUCCESS");
                })
                .addOnFailureListener(e -> {
                    hideLoadingDialog();
                    showSnackbar("Failed to remove contribution", "ERROR");
                });
    }

    private void renderExpenses() {
        binding.layoutExpensesList.removeAllViews();

        if (expensesList.isEmpty()) {
            TextView txtEmpty = new TextView(this);
            txtEmpty.setText("No event expenses logged yet.");
            txtEmpty.setPadding(8, 8, 8, 8);
            txtEmpty.setTextColor(getResources().getColor(R.color.colorTextSecondary));
            binding.layoutExpensesList.addView(txtEmpty);
            return;
        }

        for (Map<String, Object> exp : expensesList) {
            Double amount = (Double) exp.get("amount");
            String name = (String) exp.get("name");
            String note = (String) exp.get("note");
            String category = (String) exp.get("category");
            String expId = (String) exp.get("id");
            String expUid = (String) exp.get("uid");

            if (amount == null) continue;

            View row = LayoutInflater.from(this).inflate(R.layout.item_category_manage, binding.layoutExpensesList, false);
            TextView txtTitle = row.findViewById(R.id.txtCategoryName);
            TextView txtDetails = row.findViewById(R.id.txtCategoryType);
            ImageView btnDelete = row.findViewById(R.id.btnDeleteCategory);

            txtTitle.setText(String.format(Locale.US, "%s (%s)", note, category));
            txtDetails.setText(String.format(Locale.US, "Paid by %s: %s", name, CurrencyManager.getInstance().formatAmount(amount)));
            txtDetails.setVisibility(View.VISIBLE);

            // Allow delete if user is admin or logged this expense
            if (currentUid.equals(expUid)) {
                btnDelete.setVisibility(View.VISIBLE);
                btnDelete.setOnClickListener(v -> deleteExpense(expId));
            } else {
                btnDelete.setVisibility(View.GONE);
            }

            binding.layoutExpensesList.addView(row);
        }
    }

    private void deleteExpense(String expId) {
        showLoadingDialog();
        mFirestore.collection("groups").document(groupId)
                .collection("event_expenses").document(expId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    hideLoadingDialog();
                    showSnackbar("Expense removed", "SUCCESS");
                })
                .addOnFailureListener(e -> {
                    hideLoadingDialog();
                    showSnackbar("Failed to remove expense", "ERROR");
                });
    }

    private void recalculateSettlements() {
        if (groupMembers.isEmpty()) return;

        // Calculate total payments per member
        Map<String, Double> putInMap = new HashMap<>();
        for (Map<String, Object> m : groupMembers) {
            String name = (String) m.get("name");
            putInMap.put(name, 0.0);
        }

        // Add contributions
        for (Map<String, Object> cont : contributionsList) {
            String name = (String) cont.get("name");
            Double amt = (Double) cont.get("amount");
            if (name != null && amt != null && putInMap.containsKey(name)) {
                putInMap.put(name, putInMap.get(name) + amt);
            }
        }

        // Add direct expenses
        for (Map<String, Object> exp : expensesList) {
            String name = (String) exp.get("name");
            Double amt = (Double) exp.get("amount");
            if (name != null && amt != null && putInMap.containsKey(name)) {
                putInMap.put(name, putInMap.get(name) + amt);
            }
        }

        // Calculate total expenses (cost of the event)
        double totalSpend = 0.0;
        for (Map<String, Object> exp : expensesList) {
            Double amt = (Double) exp.get("amount");
            if (amt != null) {
                totalSpend += amt;
            }
        }

        // Calculate total contributions
        double totalContributed = 0.0;
        for (Map<String, Object> cont : contributionsList) {
            Double amt = (Double) cont.get("amount");
            if (amt != null) {
                totalContributed += amt;
            }
        }

        // Total flow inside fund = contributions + direct out of pocket expenses
        double totalPoolFlow = totalContributed + totalSpend;

        // We split the total flow equally among all members
        double share = totalPoolFlow / groupMembers.size();

        // Calculate balances
        Map<String, Double> balances = new HashMap<>();
        for (Map.Entry<String, Double> entry : putInMap.entrySet()) {
            double bal = entry.getValue() - share; // positive = paid more than share, negative = paid less
            balances.put(entry.getKey(), bal);
        }

        List<DebtSimplifier.Settlement> settlements = DebtSimplifier.simplify(balances);
        displaySettlements(settlements);
    }

    private void displaySettlements(List<DebtSimplifier.Settlement> settlements) {
        binding.layoutEventSettlements.removeAllViews();

        if (settlements.isEmpty()) {
            TextView txtEmpty = new TextView(this);
            txtEmpty.setText("All settled up! No transactions needed.");
            txtEmpty.setPadding(8, 8, 8, 8);
            txtEmpty.setTextColor(getResources().getColor(R.color.colorTextSecondary));
            binding.layoutEventSettlements.addView(txtEmpty);
            return;
        }

        for (DebtSimplifier.Settlement s : settlements) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_category_manage, binding.layoutEventSettlements, false);

            TextView txtFromTo = row.findViewById(R.id.txtCategoryName);
            TextView txtAmount = row.findViewById(R.id.txtCategoryType);
            ImageView btnWhatsApp = row.findViewById(R.id.btnDeleteCategory);

            txtFromTo.setText(String.format(Locale.US, "%s pays to %s", s.from, s.to));
            txtAmount.setText(CurrencyManager.getInstance().formatAmount(s.amount));
            txtAmount.setVisibility(View.VISIBLE);
            txtAmount.setTextColor(getResources().getColor(R.color.colorDanger));

            btnWhatsApp.setImageResource(R.drawable.ic_camera); // share button
            btnWhatsApp.setContentDescription("WhatsApp Reminder");
            btnWhatsApp.setVisibility(View.VISIBLE);

            // Find phone of debtor (s.from)
            String debtorPhone = "";
            for (Map<String, Object> m : groupMembers) {
                if (s.from.equals(m.get("name"))) {
                    debtorPhone = (String) m.get("phone");
                    break;
                }
            }

            final String finalPhone = debtorPhone;
            final String eventName = binding.txtEventName.getText().toString();
            btnWhatsApp.setOnClickListener(v -> {
                String message = String.format(Locale.US, "Hey %s, you owe me ₹%.2f for the event '%s'. Please transfer when convenient!", s.from, s.amount, eventName);
                if (finalPhone != null && !finalPhone.isEmpty()) {
                    String cleanPhone = finalPhone.replaceAll("[^0-9]", "");
                    if (cleanPhone.length() == 10) {
                        cleanPhone = "91" + cleanPhone;
                    }
                    try {
                        Intent waIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + cleanPhone + "?text=" + Uri.encode(message)));
                        startActivity(waIntent);
                        return;
                    } catch (Exception e) {
                        // ignore and fallback
                    }
                }
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, message);
                startActivity(Intent.createChooser(shareIntent, "Send Reminder"));
            });

            binding.layoutEventSettlements.addView(row);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (groupListener != null) groupListener.remove();
        if (contributionsListener != null) contributionsListener.remove();
        if (expensesListener != null) expensesListener.remove();
    }
}
