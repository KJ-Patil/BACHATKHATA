package com.example.bachatkhata;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.bachatkhata.domain.ReminderService;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bachatkhata.databinding.ActivityBillSplitterBinding;
import com.example.bachatkhata.databinding.ItemParticipantBinding;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BillSplitterActivity extends BaseActivity {

    private ActivityBillSplitterBinding binding;
    private FirebaseFirestore mFirestore;
    private FirebaseAuth mAuth;

    private final List<Participant> participantsList = new ArrayList<>();
    private ParticipantAdapter adapter;

    private String splitType = "Equal"; // "Equal", "Custom", "Percent"
    private final List<DocumentSnapshot> userGroups = new ArrayList<>();
    private final List<String> groupNames = new ArrayList<>();
    private ArrayAdapter<String> groupSpinnerAdapter;

    // Reference to the open "Add Participant" dialog, so contact import can close it.
    private AlertDialog addParticipantDialog;

    public static class Participant {
        public String name;
        public String phone;
        public double paid;
        public double owes;
        public double percentage;

        public Participant(String name, String phone, double paid) {
            this.name = name;
            this.phone = phone;
            this.paid = paid;
            this.owes = 0.0;
            this.percentage = 0.0;
        }

        public String getInitials() {
            if (name == null || name.trim().isEmpty()) return "?";
            String[] parts = name.trim().split("\\s+");
            if (parts.length > 1) {
                return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase(Locale.US);
            }
            return name.substring(0, 1).toUpperCase(Locale.US);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBillSplitterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mFirestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        setupUI();
        loadCurrentUserAsParticipant();
        loadGroups();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh the group list in case a group was created/edited on another screen.
        // Note: loadCurrentUserAsParticipant() is intentionally NOT called here — it appends
        // to participantsList and would duplicate the current user on every resume.
        loadGroups();
    }

    private void setupUI() {
        binding.btnBack.setOnClickListener(v -> finish());

        // Setup Recycler View
        binding.rvParticipants.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ParticipantAdapter();
        binding.rvParticipants.setAdapter(adapter);

        // Add Participant Trigger
        binding.btnAddParticipant.setOnClickListener(v -> showAddParticipantDialog());

        // Split toggle change
        binding.toggleSplitType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btnSplitEqual) {
                    splitType = "Equal";
                } else if (checkedId == R.id.btnSplitCustom) {
                    splitType = "Custom";
                } else if (checkedId == R.id.btnSplitPercent) {
                    splitType = "Percent";
                }
                recalculateEqualOwed();
                adapter.notifyDataSetChanged();
            }
        });

        // Watch Total Amount
        binding.inputAmount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                recalculateEqualOwed();
                adapter.notifyDataSetChanged();
            }
        });

        // Group selector checkbox toggle
        binding.chkSaveToGroup.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                binding.spinnerGroups.setVisibility(View.VISIBLE);
            } else {
                binding.spinnerGroups.setVisibility(View.GONE);
            }
        });

        // Calculate Trigger
        binding.btnCalculate.setOnClickListener(v -> performCalculation());
    }

    private void loadCurrentUserAsParticipant() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        mFirestore.collection("users").document(uid).get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                String name = documentSnapshot.getString("name");
                String phone = documentSnapshot.getString("phone");
                if (name == null || name.isEmpty()) {
                    name = "You";
                }
                participantsList.add(new Participant(name, phone != null ? phone : "", 0.0));
                recalculateEqualOwed();
                adapter.notifyDataSetChanged();
            }
        });
    }

    private void loadGroups() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        mFirestore.collection("groups")
                .whereArrayContains("memberUids", uid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    userGroups.clear();
                    groupNames.clear();

                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            userGroups.add(doc);
                            groupNames.add(doc.getString("name"));
                        }
                    }

                    if (groupSpinnerAdapter == null) {
                        groupSpinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, groupNames);
                        groupSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        binding.spinnerGroups.setAdapter(groupSpinnerAdapter);
                    } else {
                        groupSpinnerAdapter.notifyDataSetChanged();
                    }
                });
    }

    private void showAddParticipantDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Participant");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 16, 24, 16);

        final EditText inputName = new EditText(this);
        inputName.setHint("Name");
        layout.addView(inputName);

        final EditText inputPhone = new EditText(this);
        inputPhone.setHint("Phone Number (Optional)");
        inputPhone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        layout.addView(inputPhone);

        final com.google.android.material.button.MaterialButton btnImport =
                new com.google.android.material.button.MaterialButton(this);
        btnImport.setText("Import from Contacts");
        LinearLayout.LayoutParams importParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        importParams.topMargin = 16;
        btnImport.setLayoutParams(importParams);
        btnImport.setOnClickListener(v -> openContactPicker());
        layout.addView(btnImport);

        builder.setView(layout);

        builder.setPositiveButton("Add", (dialog, which) -> {
            String name = inputName.getText().toString().trim();
            String phone = inputPhone.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Name is required", Toast.LENGTH_SHORT).show();
                return;
            }
            participantsList.add(new Participant(name, phone, 0.0));
            recalculateEqualOwed();
            adapter.notifyDataSetChanged();
        });
        builder.setNegativeButton("Cancel", null);
        addParticipantDialog = builder.show();
    }

    private void openContactPicker() {
        ContactPickerBottomSheet picker = ContactPickerBottomSheet.newInstance(true);
        picker.setListener(this::addParticipantsFromContacts);
        picker.show(getSupportFragmentManager(), "contact_picker");
    }

    private void addParticipantsFromContacts(java.util.List<ContactPickerBottomSheet.Contact> contacts) {
        int added = 0;
        for (ContactPickerBottomSheet.Contact c : contacts) {
            boolean duplicate = false;
            for (Participant p : participantsList) {
                if (p.phone != null && !p.phone.isEmpty() && p.phone.equals(c.phone)) {
                    duplicate = true;
                    break;
                }
            }
            if (duplicate) continue;
            participantsList.add(new Participant(c.name, c.phone, 0.0));
            added++;
        }
        if (added > 0) {
            recalculateEqualOwed();
            adapter.notifyDataSetChanged();
        }
        if (addParticipantDialog != null && addParticipantDialog.isShowing()) {
            addParticipantDialog.dismiss();
        }
    }

    private double getBillAmount() {
        String amountStr = binding.inputAmount.getText().toString().trim();
        if (amountStr.isEmpty()) return 0.0;
        try {
            return Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private void recalculateEqualOwed() {
        double total = getBillAmount();
        if (total <= 0 || participantsList.isEmpty()) return;

        if ("Equal".equals(splitType)) {
            double share = total / participantsList.size();
            for (Participant p : participantsList) {
                p.owes = share;
                p.percentage = 100.0 / participantsList.size();
            }
        }
    }

    private void showEditShareDialog(Participant p) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Set Share for " + p.name);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 16, 24, 16);

        final EditText inputVal = new EditText(this);
        inputVal.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);

        if ("Percent".equals(splitType)) {
            builder.setMessage("Enter percentage of bill:");
            inputVal.setHint("Percentage (e.g. 25)");
            inputVal.setText(String.format(Locale.US, "%.1f", p.percentage));
        } else {
            builder.setMessage("Enter amount owed (₹):");
            inputVal.setHint("Amount (e.g. 500)");
            inputVal.setText(String.format(Locale.US, "%.2f", p.owes));
        }

        layout.addView(inputVal);
        builder.setView(layout);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String valStr = inputVal.getText().toString().trim();
            if (valStr.isEmpty()) return;

            try {
                double val = Double.parseDouble(valStr);
                double total = getBillAmount();

                if ("Percent".equals(splitType)) {
                    p.percentage = val;
                    p.owes = total * val / 100.0;
                } else {
                    p.owes = val;
                    p.percentage = total > 0 ? (val / total) * 100.0 : 0.0;
                }
                adapter.notifyDataSetChanged();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number format", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void performCalculation() {
        double totalAmount = getBillAmount();
        if (totalAmount <= 0) {
            showSnackbar("Please enter a valid bill amount", "ERROR");
            return;
        }

        String title = binding.inputTitle.getText().toString().trim();
        if (title.isEmpty()) {
            title = "Split Session";
        }

        // Validate splits sum
        double totalOwed = 0.0;
        double totalPaid = 0.0;
        for (Participant p : participantsList) {
            totalOwed += p.owes;
            totalPaid += p.paid;
        }

        if ("Custom".equals(splitType) && Math.abs(totalOwed - totalAmount) > 1.0) {
            showSnackbar(String.format(Locale.US, "Sum of individual owes (₹%.2f) must match total bill (₹%.2f)", totalOwed, totalAmount), "ERROR");
            return;
        }

        if ("Percent".equals(splitType)) {
            double totalPct = 0.0;
            for (Participant p : participantsList) {
                totalPct += p.percentage;
            }
            if (Math.abs(totalPct - 100.0) > 0.5) {
                showSnackbar(String.format(Locale.US, "Sum of percentages (%.1f%%) must equal 100%%", totalPct), "ERROR");
                return;
            }
        }

        // Build net balances
        Map<String, Double> balances = new HashMap<>();
        for (Participant p : participantsList) {
            double balance = p.paid - p.owes; // Positive = owed to them, Negative = they owe
            balances.put(p.name, balance);
        }

        List<DebtSimplifier.Settlement> settlements = DebtSimplifier.simplify(balances);
        displaySettlements(settlements, title);

        // Optionally Save to Group
        if (binding.chkSaveToGroup.isChecked() && !userGroups.isEmpty()) {
            int selectedIndex = binding.spinnerGroups.getSelectedItemPosition();
            if (selectedIndex >= 0 && selectedIndex < userGroups.size()) {
                String groupId = userGroups.get(selectedIndex).getId();
                saveSplitSessionToGroup(groupId, title, totalAmount, settlements);
            }
        }
    }

    private void displaySettlements(List<DebtSimplifier.Settlement> settlements, String title) {
        binding.layoutSettlements.removeAllViews();
        binding.cardSettlements.setVisibility(View.VISIBLE);

        if (settlements.isEmpty()) {
            TextView txtEmpty = new TextView(this);
            txtEmpty.setText("All settled up! No transactions needed.");
            txtEmpty.setPadding(8, 8, 8, 8);
            txtEmpty.setTextColor(getResources().getColor(R.color.colorTextSecondary));
            binding.layoutSettlements.addView(txtEmpty);
            return;
        }

        for (DebtSimplifier.Settlement s : settlements) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_category_manage, binding.layoutSettlements, false);

            TextView txtFromTo = row.findViewById(R.id.txtCategoryName);
            TextView txtAmount = row.findViewById(R.id.txtCategoryType);
            ImageView btnWhatsApp = row.findViewById(R.id.btnDeleteCategory);

            txtFromTo.setText(String.format(Locale.US, "%s pays to %s", s.from, s.to));
            txtAmount.setText(CurrencyManager.getInstance().formatAmount(s.amount));
            txtAmount.setVisibility(View.VISIBLE);
            txtAmount.setTextColor(getResources().getColor(R.color.colorDanger));

            btnWhatsApp.setImageResource(R.drawable.ic_camera); // We can use ic_camera or any social/icon we have. Wait, does a whatsapp icon exist? Let's check drawable, if not just set share icon or use ic_camera/ic_launcher.
            btnWhatsApp.setContentDescription("WhatsApp Reminder");
            btnWhatsApp.setVisibility(View.VISIBLE);

            // Find phone of the debtor (s.from) to message them
            String debtorPhone = "";
            for (Participant p : participantsList) {
                if (p.name.equals(s.from)) {
                    debtorPhone = p.phone;
                    break;
                }
            }

            final String finalDebtorPhone = debtorPhone;
            final String finalTitle = title;
            btnWhatsApp.setOnClickListener(v -> triggerWhatsAppReminder(s.from, finalDebtorPhone, s.amount, finalTitle));

            binding.layoutSettlements.addView(row);
        }
    }

    /**
     * Split settlements use the SETTLEMENT relation — between equals sharing a
     * bill, not a creditor chasing a debtor.
     *
     * <p>With no phone number on file the composer's send buttons have nowhere to
     * go, so that case falls back to a generic share sheet instead.
     */
    private void triggerWhatsAppReminder(String debtorName, String phone, double amount, String title) {
        String formattedAmount = CurrencyManager.getInstance().formatAmount(amount);

        if (phone != null && !phone.trim().isEmpty()) {
            FlashReminderBottomSheet.newInstance(debtorName, phone, formattedAmount,
                            ReminderService.Relation.SETTLEMENT)
                    .show(getSupportFragmentManager(), "FlashReminderBottomSheet");
            return;
        }

        String message = ReminderService.generate(debtorName, formattedAmount,
                ReminderService.Tone.FRIENDLY, ReminderService.Lang.EN,
                ReminderService.Relation.SETTLEMENT);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, message);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.reminder_title)));
    }

    private void saveSplitSessionToGroup(String groupId, String title, double totalAmount, List<DebtSimplifier.Settlement> settlements) {
        Map<String, Object> session = new HashMap<>();
        session.put("title", title);
        session.put("totalAmount", totalAmount);
        session.put("splitType", splitType);
        session.put("createdAt", Timestamp.now());

        List<Map<String, Object>> pts = new ArrayList<>();
        for (Participant p : participantsList) {
            Map<String, Object> map = new HashMap<>();
            map.put("name", p.name);
            map.put("paid", p.paid);
            map.put("owes", p.owes);
            pts.add(map);
        }
        session.put("participants", pts);

        List<Map<String, Object>> sets = new ArrayList<>();
        for (DebtSimplifier.Settlement s : settlements) {
            Map<String, Object> map = new HashMap<>();
            map.put("from", s.from);
            map.put("to", s.to);
            map.put("amount", s.amount);
            sets.add(map);
        }
        session.put("settlements", sets);

        mFirestore.collection("groups").document(groupId)
                .collection("split_sessions").add(session)
                .addOnSuccessListener(ref -> showSnackbar("Split session synced to group wallet!", "SUCCESS"))
                .addOnFailureListener(e -> showSnackbar("Failed to sync session online", "ERROR"));
    }

    // Participant RecyclerView Adapter
    private class ParticipantAdapter extends RecyclerView.Adapter<ParticipantAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemParticipantBinding binding = ItemParticipantBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new Holder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Participant p = participantsList.get(position);
            holder.bind(p);
        }

        @Override
        public int getItemCount() {
            return participantsList.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            ItemParticipantBinding pBinding;

            Holder(ItemParticipantBinding binding) {
                super(binding.getRoot());
                pBinding = binding;
            }

            void bind(Participant p) {
                pBinding.txtParticipantName.setText(p.name);
                pBinding.txtInitials.setText(p.getInitials());

                if ("Equal".equals(splitType)) {
                    pBinding.txtOwesLabel.setText(String.format(Locale.US, "Owes: ₹%.2f", p.owes));
                } else if ("Percent".equals(splitType)) {
                    pBinding.txtOwesLabel.setText(String.format(Locale.US, "Owes: ₹%.2f (%.1f%%)", p.owes, p.percentage));
                } else {
                    pBinding.txtOwesLabel.setText(String.format(Locale.US, "Owes: ₹%.2f", p.owes));
                }

                // If Custom or Percentage, allow clicking owes label to set it
                if (!"Equal".equals(splitType)) {
                    pBinding.txtOwesLabel.setClickable(true);
                    pBinding.txtOwesLabel.setFocusable(true);
                    pBinding.txtOwesLabel.setBackgroundResource(R.drawable.bg_clay_button); // visually indicate clickable
                    pBinding.txtOwesLabel.setOnClickListener(v -> showEditShareDialog(p));
                } else {
                    pBinding.txtOwesLabel.setClickable(false);
                    pBinding.txtOwesLabel.setFocusable(false);
                    pBinding.txtOwesLabel.setBackground(null);
                }

                // Prevent text watcher recursive trigger loops
                pBinding.inputPaid.setOnFocusChangeListener(null);
                pBinding.inputPaid.setText(p.paid > 0 ? String.format(Locale.US, "%.2f", p.paid) : "");
                
                // Use a TextWatcher to sync inputPaid
                pBinding.inputPaid.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}
                    @Override
                    public void afterTextChanged(Editable s) {
                        String paidStr = s.toString().trim();
                        if (!paidStr.isEmpty()) {
                            try {
                                p.paid = Double.parseDouble(paidStr);
                            } catch (NumberFormatException e) {
                                p.paid = 0.0;
                            }
                        } else {
                            p.paid = 0.0;
                        }
                    }
                });

                // Remove participant button (except current user which is index 0)
                if (getAdapterPosition() == 0) {
                    pBinding.btnRemoveParticipant.setVisibility(View.GONE);
                } else {
                    pBinding.btnRemoveParticipant.setVisibility(View.VISIBLE);
                    pBinding.btnRemoveParticipant.setOnClickListener(v -> {
                        int pos = getAdapterPosition();
                        if (pos != RecyclerView.NO_POSITION) {
                            participantsList.remove(pos);
                            recalculateEqualOwed();
                            notifyDataSetChanged();
                        }
                    });
                }
            }
        }
    }
}
