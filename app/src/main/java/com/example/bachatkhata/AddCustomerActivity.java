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
import com.google.firebase.firestore.WriteBatch;

public class AddCustomerActivity extends BaseActivity {

    private static final int REQUEST_CODE_PICK_CONTACT = 1002;

    private ActivityAddCustomerBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddCustomerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();

        setupListeners();

        // "They owe you" is the common case for a customer khata, so it is the
        // pre-selected direction rather than leaving the pair unchecked.
        binding.toggleOpeningDirection.check(R.id.btnOpeningTheyOwe);
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

        // Reject a mistyped number here — a bad one saves fine but silently breaks
        // the WhatsApp/SMS reminder links that depend on it later.
        String phoneError = Country.validateLoosePhone(phone);
        if (phoneError != null) {
            showError(phoneError);
            return;
        }

        // A khata is usually opened because money is already owed. Parsing this
        // before any write so a typo fails the form rather than half-creating
        // a contact whose opening entry then never lands.
        String openingText = binding.etOpeningBalance.getText().toString().trim();
        double openingBalance = 0;
        if (!openingText.isEmpty()) {
            try {
                openingBalance = Double.parseDouble(openingText);
            } catch (NumberFormatException e) {
                showError("Opening balance is not a valid amount.");
                return;
            }
            if (openingBalance < 0) {
                showError("Opening balance cannot be negative — use the toggle to pick the direction.");
                return;
            }
        }

        if (mAuth.getCurrentUser() == null) return;
        showLoading(true);

        String uid = mAuth.getCurrentUser().getUid();
        DocumentReference docRef = mFirestore.collection("users").document(uid).collection("customers").document();
        String customerId = docRef.getId();

        // The customer document always starts at zero; the opening balance arrives
        // through the same entry path as every other one, so it is visible in the
        // history and reversible, instead of an unexplained starting number.
        Customer customer = new Customer(customerId, name, phone, 0.0, Timestamp.now());

        WriteBatch batch = mFirestore.batch();
        batch.set(docRef, customer.toMap());

        if (openingBalance > 0) {
            boolean theyOweYou = binding.toggleOpeningDirection.getCheckedButtonId() != R.id.btnOpeningYouOwe;
            openingBalance = CurrencyManager.getInstance().toBaseAmount(openingBalance);
            LedgerMirror.queueEntry(mFirestore, batch, uid, customer, openingBalance,
                    theyOweYou ? LedgerMirror.TYPE_GAVE : LedgerMirror.TYPE_GOT,
                    getString(R.string.ledger_opening_balance_note), new java.util.Date());
        }

        batch.commit()
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
