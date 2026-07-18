package com.example.bachatkhata;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bachatkhata.databinding.DialogAddAccountBookBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

public class AddAccountBookBottomSheet extends BottomSheetDialogFragment {

    private DialogAddAccountBookBinding binding;
    private String accountType = "customer";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = DialogAddAccountBookBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Show currency code in balance label
        String currencyCode = CurrencyManager.getInstance().getCurrentCurrencyCode();
        binding.txtBalanceLabel.setText("OPENING BALANCE (" + currencyCode + ")");

        setupToggle();
        setupListeners();
    }

    private void setupToggle() {
        applyToggleState("customer");

        binding.toggleType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnTypeCustomer) {
                accountType = "customer";
                applyToggleState("customer");
            } else {
                accountType = "supplier";
                applyToggleState("supplier");
            }
        });
    }

    private void applyToggleState(String type) {
        if ("customer".equals(type)) {
            binding.btnTypeCustomer.setBackgroundColor(Color.parseColor("#7C6FE0"));
            binding.btnTypeCustomer.setTextColor(Color.WHITE);
            binding.btnTypeSupplier.setBackgroundColor(Color.TRANSPARENT);
            binding.btnTypeSupplier.setTextColor(Color.parseColor("#7C6FE0"));
        } else {
            binding.btnTypeSupplier.setBackgroundColor(Color.parseColor("#7C6FE0"));
            binding.btnTypeSupplier.setTextColor(Color.WHITE);
            binding.btnTypeCustomer.setBackgroundColor(Color.TRANSPARENT);
            binding.btnTypeCustomer.setTextColor(Color.parseColor("#7C6FE0"));
        }
    }

    private void setupListeners() {
        binding.btnClose.setOnClickListener(v -> dismiss());
        binding.btnCancel.setOnClickListener(v -> dismiss());
        binding.btnImportContact.setOnClickListener(v -> pickContact());
        binding.btnCreate.setOnClickListener(v -> createBook());
    }

    private void pickContact() {
        ContactPickerBottomSheet picker = ContactPickerBottomSheet.newInstance(false);
        picker.setListener(contacts -> {
            if (contacts.isEmpty() || binding == null) return;
            ContactPickerBottomSheet.Contact c = contacts.get(0);
            binding.etName.setText(c.name);
            binding.etPhone.setText(c.phone);
        });
        picker.show(getChildFragmentManager(), "contact_picker");
    }

    private void createBook() {
        String name = binding.etName.getText() != null
                ? binding.etName.getText().toString().trim() : "";
        String phone = binding.etPhone.getText() != null
                ? binding.etPhone.getText().toString().trim() : "";
        String balanceStr = binding.etOpeningBalance.getText() != null
                ? binding.etOpeningBalance.getText().toString().trim() : "0";

        if (name.isEmpty()) {
            binding.tilName.setError("Please enter account name");
            return;
        }
        binding.tilName.setError(null);

        double openingBalance = 0.0;
        try {
            openingBalance = Double.parseDouble(balanceStr.isEmpty() ? "0" : balanceStr);
        } catch (NumberFormatException ignored) {}

        if ("supplier".equals(accountType)) {
            openingBalance = -Math.abs(openingBalance);
        }

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Toast.makeText(requireContext(), "Session expired. Please sign in again.", Toast.LENGTH_LONG).show();
            return;
        }

        binding.btnCreate.setEnabled(false);
        String uid = auth.getCurrentUser().getUid();

        DocumentReference docRef = FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("customers").document();
        String customerId = docRef.getId();

        Customer customer = new Customer(customerId, name, phone, accountType,
                openingBalance, Timestamp.now());

        docRef.set(customer.toMap())
                .addOnSuccessListener(aVoid -> {
                    if (isAdded()) dismiss();
                })
                .addOnFailureListener(e -> {
                    Log.e("AddAccountBook", "Firestore write failed", e);
                    // Toast is always visible — unlike Snackbar it doesn't need a CoordinatorLayout
                    Toast.makeText(requireContext(),
                            "Failed to save: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    // Re-enable button only if the view is still alive
                    if (binding != null) {
                        binding.btnCreate.setEnabled(true);
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
