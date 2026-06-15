package com.example.bachatkhata;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bachatkhata.databinding.ActivityCustomerDetailBinding;
import com.example.bachatkhata.databinding.ItemCustomerTxnBinding;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CustomerDetailActivity extends BaseActivity {

    private ActivityCustomerDetailBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;

    private Customer customer;
    private final List<CustomerTransaction> transactionsList = new ArrayList<>();
    private TransactionAdapter adapter;
    private ListenerRegistration customerListener;
    private ListenerRegistration transactionsListener;

    private boolean isGave = true; // "You Gave" default
    private Date selectedDate = new Date();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCustomerDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();

        customer = (Customer) getIntent().getSerializableExtra("customer");
        if (customer == null) {
            finish();
            return;
        }

        setupRecyclerView();
        setupToggleGroup();
        setupDatePicker();
        setupListeners();
        observeCustomer();
        observeTransactions();
    }

    private void setupRecyclerView() {
        adapter = new TransactionAdapter();
        binding.rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        binding.rvTransactions.setAdapter(adapter);
    }

    private void setupToggleGroup() {
        // Default color for "You Gave"
        binding.btnTypeGave.setBackgroundColor(Color.parseColor("#3DAF85")); // green
        binding.btnTypeGave.setTextColor(Color.WHITE);
        binding.btnTypeGot.setBackgroundColor(Color.TRANSPARENT);
        binding.btnTypeGot.setTextColor(Color.parseColor("#7C6FE0"));

        binding.toggleEntryType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnTypeGave) {
                isGave = true;
                binding.btnTypeGave.setBackgroundColor(Color.parseColor("#3DAF85"));
                binding.btnTypeGave.setTextColor(Color.WHITE);
                binding.btnTypeGot.setBackgroundColor(Color.TRANSPARENT);
                binding.btnTypeGot.setTextColor(Color.parseColor("#7C6FE0"));
            } else {
                isGave = false;
                binding.btnTypeGot.setBackgroundColor(Color.parseColor("#E24B4A")); // red
                binding.btnTypeGot.setTextColor(Color.WHITE);
                binding.btnTypeGave.setBackgroundColor(Color.TRANSPARENT);
                binding.btnTypeGave.setTextColor(Color.parseColor("#7C6FE0"));
            }
        });
    }

    private void setupDatePicker() {
        binding.txtSelectedDate.setText(dateFormat.format(selectedDate));
        binding.cardDatePicker.setOnClickListener(v -> {
            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Select Date")
                    .setSelection(selectedDate.getTime())
                    .build();

            datePicker.addOnPositiveButtonClickListener(selection -> {
                selectedDate = new Date(selection);
                binding.txtSelectedDate.setText(dateFormat.format(selectedDate));
            });

            datePicker.show(getSupportFragmentManager(), "DATE_PICKER");
        });
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnSaveEntry.setOnClickListener(v -> saveEntry());
        binding.btnSendReminder.setOnClickListener(v -> sendWhatsAppReminder());
        binding.btnGeneratePdf.setOnClickListener(v -> generatePdfStatement());
    }

    private void observeCustomer() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        customerListener = mFirestore.collection("users").document(uid)
                .collection("customers").document(customer.getId())
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) return;
                    if (snapshot != null && snapshot.exists()) {
                        customer = Customer.fromDocument(snapshot);
                        updateCustomerHeaderUI();
                    }
                });
    }

    private void observeTransactions() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        transactionsListener = mFirestore.collection("users").document(uid)
                .collection("customer_txns")
                .whereEqualTo("customerId", customer.getId())
                .orderBy("date", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;

                    transactionsList.clear();
                    if (value != null) {
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            transactionsList.add(CustomerTransaction.fromDocument(doc));
                        }
                    }

                    if (binding != null) {
                        if (transactionsList.isEmpty()) {
                            binding.txtEmptyTransactions.setVisibility(View.VISIBLE);
                            binding.rvTransactions.setVisibility(View.GONE);
                        } else {
                            binding.txtEmptyTransactions.setVisibility(View.GONE);
                            binding.rvTransactions.setVisibility(View.VISIBLE);
                        }
                        adapter.notifyDataSetChanged();
                    }
                });
    }

    private void updateCustomerHeaderUI() {
        binding.txtCustomerName.setText(customer.getName());
        binding.txtCustomerPhone.setText(customer.getPhone());

        if (customer.getName() != null && !customer.getName().trim().isEmpty()) {
            binding.txtAvatarLetter.setText(customer.getName().substring(0, 1).toUpperCase(Locale.US));
        } else {
            binding.txtAvatarLetter.setText("C");
        }

        double balance = customer.getBalance();
        String formattedBalance = CurrencyManager.getInstance().formatAmount(Math.abs(balance));
        binding.txtBalanceAmount.setText(formattedBalance);

        if (balance > 0) {
            binding.txtBalanceAmount.setTextColor(Color.parseColor("#3DAF85")); // green
            binding.txtBalanceLabel.setText("You will get");
            binding.txtBalanceLabel.setTextColor(Color.parseColor("#3DAF85"));
            binding.layoutAvatarBg.setBackgroundColor(Color.parseColor("#1A3DAF85"));
            binding.txtAvatarLetter.setTextColor(Color.parseColor("#3DAF85"));
        } else if (balance < 0) {
            binding.txtBalanceAmount.setTextColor(Color.parseColor("#E24B4A")); // red
            binding.txtBalanceLabel.setText("You will pay");
            binding.txtBalanceLabel.setTextColor(Color.parseColor("#E24B4A"));
            binding.layoutAvatarBg.setBackgroundColor(Color.parseColor("#1AE24B4A"));
            binding.txtAvatarLetter.setTextColor(Color.parseColor("#E24B4A"));
        } else {
            binding.txtBalanceAmount.setTextColor(Color.parseColor("#1A1A2E"));
            binding.txtBalanceLabel.setText("Settled");
            binding.txtBalanceLabel.setTextColor(Color.parseColor("#6B6B8A"));
            binding.layoutAvatarBg.setBackgroundColor(Color.parseColor("#E0DEFF"));
            binding.txtAvatarLetter.setTextColor(Color.parseColor("#7C6FE0"));
        }
    }

    private void saveEntry() {
        String amountStr = binding.etEntryAmount.getText().toString().trim();
        if (amountStr.isEmpty()) {
            showError("Please enter amount.");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            showError("Invalid amount.");
            return;
        }

        if (amount <= 0) {
            showError("Amount must be greater than zero.");
            return;
        }

        String note = binding.etEntryNote.getText().toString().trim();
        if (note.isEmpty()) {
            note = isGave ? "You Gave" : "You Got";
        }

        if (mAuth.getCurrentUser() == null) return;
        showLoading(true);

        String uid = mAuth.getCurrentUser().getUid();

        WriteBatch batch = mFirestore.batch();

        DocumentReference txnRef = mFirestore.collection("users").document(uid)
                .collection("customer_txns").document();

        CustomerTransaction transaction = new CustomerTransaction(
                txnRef.getId(),
                customer.getId(),
                amount,
                note,
                new Timestamp(selectedDate),
                isGave ? "gave" : "got"
        );

        // Save transaction record
        batch.set(txnRef, transaction.toMap());

        // Increment customer balance atomically
        double balanceChange = isGave ? amount : -amount;
        DocumentReference customerRef = mFirestore.collection("users").document(uid)
                .collection("customers").document(customer.getId());
        batch.update(customerRef, "balance", FieldValue.increment(balanceChange));

        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    showSuccess("Entry recorded successfully!");
                    binding.etEntryAmount.setText("");
                    binding.etEntryNote.setText("");
                    selectedDate = new Date();
                    binding.txtSelectedDate.setText(dateFormat.format(selectedDate));
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    showError("Failed to record entry: " + e.getLocalizedMessage());
                });
    }

    private void sendWhatsAppReminder() {
        String phone = customer.getPhone();
        if (phone == null || phone.trim().isEmpty()) return;

        String cleanPhone = phone.replaceAll("[^0-9]", "");
        if (cleanPhone.length() == 10) {
            cleanPhone = "91" + cleanPhone;
        }

        double balance = customer.getBalance();
        String text;
        if (balance > 0) {
            text = String.format(Locale.US,
                    "Hello %s, this is a friendly reminder that a pending balance of %s is due. Please settle it soon. Thank you!",
                    customer.getName(),
                    CurrencyManager.getInstance().formatAmount(balance)
            );
        } else if (balance < 0) {
            text = String.format(Locale.US,
                    "Hello %s, please share your details so I can clear the pending balance of %s that I owe you. Thank you!",
                    customer.getName(),
                    CurrencyManager.getInstance().formatAmount(Math.abs(balance))
            );
        } else {
            text = String.format(Locale.US, "Hello %s, our ledger balance is currently settled. Thank you!", customer.getName());
        }

        try {
            Uri uri = Uri.parse("https://wa.me/" + cleanPhone + "?text=" + URLEncoder.encode(text, "UTF-8"));
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    private void generatePdfStatement() {
        showLoading(true);
        PdfDocument pdfDocument = new PdfDocument();
        Paint paint = new Paint();
        Paint titlePaint = new Paint();

        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = pdfDocument.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        // 1. Draw Title
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        titlePaint.setTextSize(20);
        titlePaint.setColor(Color.parseColor("#7C6FE0"));
        canvas.drawText("BachatKhata Ledger Statement", 40, 50, titlePaint);

        // 2. Draw Metadata
        paint.setTextSize(12);
        paint.setColor(Color.BLACK);
        canvas.drawText("Statement Date: " + displayDateFormat().format(new Date()), 40, 80, paint);
        canvas.drawText("Client Name: " + customer.getName(), 40, 100, paint);
        canvas.drawText("Phone Number: " + customer.getPhone(), 40, 120, paint);

        double balance = customer.getBalance();
        String balStr = CurrencyManager.getInstance().formatAmount(balance);
        if (balance > 0) {
            paint.setColor(Color.parseColor("#3DAF85"));
            canvas.drawText("Net Balance: They owe you " + balStr, 40, 140, paint);
        } else if (balance < 0) {
            paint.setColor(Color.parseColor("#E24B4A"));
            canvas.drawText("Net Balance: You owe them " + CurrencyManager.getInstance().formatAmount(Math.abs(balance)), 40, 140, paint);
        } else {
            paint.setColor(Color.GRAY);
            canvas.drawText("Net Balance: Settled (₹0.00)", 40, 140, paint);
        }

        paint.setColor(Color.BLACK);
        canvas.drawText("----------------------------------------------------------------------------------------------------", 40, 165, paint);

        // 3. Draw Table Headers
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText("Date", 40, 185, paint);
        canvas.drawText("Note", 140, 185, paint);
        canvas.drawText("Type", 340, 185, paint);
        canvas.drawText("Amount", 480, 185, paint);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));

        canvas.drawText("----------------------------------------------------------------------------------------------------", 40, 200, paint);

        int y = 220;
        SimpleDateFormat rowFmt = new SimpleDateFormat("dd MMM yyyy", Locale.US);

        for (CustomerTransaction txn : transactionsList) {
            if (y > 780) {
                // If text reaches page bottom, start new page
                pdfDocument.finishPage(page);
                pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 2).create();
                page = pdfDocument.startPage(pageInfo);
                canvas = page.getCanvas();
                y = 50;
            }

            String dateStr = txn.getDate() != null ? rowFmt.format(txn.getDate().toDate()) : "";
            String noteStr = txn.getNote() != null ? txn.getNote() : "";
            String typeStr = "gave".equals(txn.getType()) ? "You Gave" : "You Got";
            String amtStr = CurrencyManager.getInstance().formatAmount(txn.getAmount());

            canvas.drawText(dateStr, 40, y, paint);
            
            // Truncate note if it is too long for the column
            if (noteStr.length() > 24) {
                noteStr = noteStr.substring(0, 21) + "...";
            }
            canvas.drawText(noteStr, 140, y, paint);
            
            if ("gave".equals(txn.getType())) {
                paint.setColor(Color.parseColor("#3DAF85"));
            } else {
                paint.setColor(Color.parseColor("#E24B4A"));
            }
            canvas.drawText(typeStr, 340, y, paint);
            canvas.drawText(amtStr, 480, y, paint);
            
            paint.setColor(Color.BLACK); // reset

            y += 25;
        }

        pdfDocument.finishPage(page);

        // Write the PDF file to internal cache directory
        File cacheDir = getCacheDir();
        File statementFile = new File(cacheDir, "Statement_" + customer.getId() + ".pdf");

        try (FileOutputStream fos = new FileOutputStream(statementFile)) {
            pdfDocument.writeTo(fos);
            pdfDocument.close();

            showLoading(false);
            sharePdf(statementFile);
        } catch (IOException e) {
            pdfDocument.close();
            showLoading(false);
            showError("Failed to generate PDF: " + e.getLocalizedMessage());
        }
    }

    private void sharePdf(File file) {
        Uri fileUri = FileProvider.getUriForFile(
                this,
                "com.example.bachatkhata.fileprovider",
                file
        );

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM, fileUri);
        intent.putExtra(Intent.EXTRA_SUBJECT, "Account Statement - " + customer.getName());
        intent.putExtra(Intent.EXTRA_TEXT, "Please find attached the account statement for " + customer.getName());
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        startActivity(Intent.createChooser(intent, "Share Account Statement"));
    }

    private SimpleDateFormat displayDateFormat() {
        return new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (customerListener != null) {
            customerListener.remove();
        }
        if (transactionsListener != null) {
            transactionsListener.remove();
        }
        binding = null;
    }

    // Adapter for Transactions
    private class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.ViewHolder> {

        private final SimpleDateFormat rowDateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.US);

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemCustomerTxnBinding itemBinding = ItemCustomerTxnBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(transactionsList.get(position));
        }

        @Override
        public int getItemCount() {
            return transactionsList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final ItemCustomerTxnBinding itemBinding;

            public ViewHolder(ItemCustomerTxnBinding itemBinding) {
                super(itemBinding.getRoot());
                this.itemBinding = itemBinding;
            }

            public void bind(CustomerTransaction txn) {
                itemBinding.txtTxnNote.setText(txn.getNote());
                if (txn.getDate() != null) {
                    itemBinding.txtTxnDate.setText(rowDateFormat.format(txn.getDate().toDate()));
                } else {
                    itemBinding.txtTxnDate.setText("");
                }

                String formattedAmount = CurrencyManager.getInstance().formatAmount(txn.getAmount());
                itemBinding.txtTxnAmount.setText(formattedAmount);

                if ("gave".equals(txn.getType())) {
                    itemBinding.txtTxnAmount.setTextColor(Color.parseColor("#3DAF85")); // green
                    itemBinding.txtTxnTypeLabel.setText("You Gave");
                    itemBinding.txtTxnTypeLabel.setTextColor(Color.parseColor("#3DAF85"));
                } else {
                    itemBinding.txtTxnAmount.setTextColor(Color.parseColor("#E24B4A")); // red
                    itemBinding.txtTxnTypeLabel.setText("You Got");
                    itemBinding.txtTxnTypeLabel.setTextColor(Color.parseColor("#E24B4A"));
                }
            }
        }
    }
}
