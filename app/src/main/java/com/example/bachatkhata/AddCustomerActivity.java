package com.example.bachatkhata;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.bachatkhata.databinding.ActivityAddCustomerBinding;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

public class AddCustomerActivity extends BaseActivity {

    private static final int REQUEST_CODE_PICK_CONTACT = 1002;

    private ActivityAddCustomerBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;

    private String customerType = "customer"; // default: customer

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddCustomerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();

        setupToggleGroup();
        setupListeners();
    }

    private void setupToggleGroup() {
        // Default color for Customer
        binding.btnTypeCustomer.setBackgroundColor(Color.parseColor("#7C6FE0")); // colorPrimary
        binding.btnTypeCustomer.setTextColor(Color.WHITE);
        binding.btnTypeSupplier.setBackgroundColor(Color.TRANSPARENT);
        binding.btnTypeSupplier.setTextColor(Color.parseColor("#7C6FE0"));

        binding.toggleCustomerType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnTypeCustomer) {
                customerType = "customer";
                binding.btnTypeCustomer.setBackgroundColor(Color.parseColor("#7C6FE0"));
                binding.btnTypeCustomer.setTextColor(Color.WHITE);
                binding.btnTypeSupplier.setBackgroundColor(Color.TRANSPARENT);
                binding.btnTypeSupplier.setTextColor(Color.parseColor("#7C6FE0"));
            } else {
                customerType = "supplier";
                binding.btnTypeSupplier.setBackgroundColor(Color.parseColor("#7C6FE0"));
                binding.btnTypeSupplier.setTextColor(Color.WHITE);
                binding.btnTypeCustomer.setBackgroundColor(Color.TRANSPARENT);
                binding.btnTypeCustomer.setTextColor(Color.parseColor("#7C6FE0"));
            }
        });
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnImportContact.setOnClickListener(v -> pickContact());
        binding.btnSaveCustomer.setOnClickListener(v -> saveCustomer());
    }

    private void pickContact() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        startActivityForResult(intent, REQUEST_CODE_PICK_CONTACT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_CONTACT && resultCode == RESULT_OK && data != null) {
            Uri contactUri = data.getData();
            String[] projection = new String[]{
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            };
            try (Cursor cursor = getContentResolver().query(contactUri, projection, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                    int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                    String number = cursor.getString(numberIndex);
                    String name = cursor.getString(nameIndex);

                    binding.etCustomerName.setText(name);
                    binding.etCustomerPhone.setText(number);
                }
            } catch (Exception e) {
                showError("Failed to import contact details: " + e.getMessage());
            }
        }
    }

    private void saveCustomer() {
        String name = binding.etCustomerName.getText().toString().trim();
        String phone = binding.etCustomerPhone.getText().toString().trim();

        if (name.isEmpty()) {
            showError("Please enter contact name.");
            return;
        }

        if (phone.isEmpty()) {
            showError("Please enter phone number.");
            return;
        }

        if (mAuth.getCurrentUser() == null) return;
        showLoading(true);

        String uid = mAuth.getCurrentUser().getUid();
        DocumentReference docRef = mFirestore.collection("users").document(uid).collection("customers").document();
        String customerId = docRef.getId();

        Customer customer = new Customer(customerId, name, phone, customerType, 0.0, Timestamp.now());

        docRef.set(customer.toMap())
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    showSuccess("Contact added to ledger!");
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::finish, 800);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    showError("Failed to save contact: " + e.getMessage());
                });
    }

    private void showLoading(boolean isLoading) {
        binding.loaderOverlay.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        Snackbar snackbar = Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG);
        snackbar.setBackgroundTint(getResources().getColor(R.color.colorDanger));
        snackbar.show();
    }

    private void showSuccess(String message) {
        Snackbar snackbar = Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG);
        snackbar.setBackgroundTint(getResources().getColor(R.color.colorSecondary));
        snackbar.show();
    }
}
