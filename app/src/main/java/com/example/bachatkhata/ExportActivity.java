package com.example.bachatkhata;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.bachatkhata.databinding.ActivityExportBinding;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ExportActivity extends BaseActivity {

    private ActivityExportBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;

    private Date startDate;
    private Date endDate;
    private final SimpleDateFormat displayDateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityExportBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();

        initDates();
        setupListeners();
    }

    private void initDates() {
        Calendar cal = Calendar.getInstance();
        endDate = cal.getTime();

        // Start date defaults to 30 days ago
        cal.add(Calendar.DAY_OF_MONTH, -30);
        startDate = cal.getTime();

        binding.txtStartDate.setText(String.format("Start Date: %s", displayDateFormat.format(startDate)));
        binding.txtEndDate.setText(String.format("End Date: %s", displayDateFormat.format(endDate)));
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());

        binding.cardStartDate.setOnClickListener(v -> {
            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Select Start Date")
                    .setSelection(startDate.getTime())
                    .build();

            datePicker.addOnPositiveButtonClickListener(selection -> {
                startDate = new Date(selection);
                binding.txtStartDate.setText(String.format("Start Date: %s", displayDateFormat.format(startDate)));
            });

            datePicker.show(getSupportFragmentManager(), "START_DATE_PICKER");
        });

        binding.cardEndDate.setOnClickListener(v -> {
            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Select End Date")
                    .setSelection(endDate.getTime())
                    .build();

            datePicker.addOnPositiveButtonClickListener(selection -> {
                endDate = new Date(selection);
                binding.txtEndDate.setText(String.format("End Date: %s", displayDateFormat.format(endDate)));
            });

            datePicker.show(getSupportFragmentManager(), "END_DATE_PICKER");
        });

        binding.btnExport.setOnClickListener(v -> performExport());
    }

    private void performExport() {
        if (startDate.after(endDate)) {
            showError("Start date cannot be after end date.");
            return;
        }

        if (mAuth.getCurrentUser() == null) return;
        showLoading(true);
        String uid = mAuth.getCurrentUser().getUid();

        // Set start boundary to start of day, end boundary to end of day
        Calendar cal = Calendar.getInstance();
        cal.setTime(startDate);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        Date queryStartDate = cal.getTime();

        cal.setTime(endDate);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        Date queryEndDate = cal.getTime();

        mFirestore.collection("users").document(uid)
                .collection("transactions")
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Transaction> filteredList = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        Transaction t = Transaction.fromDocument(doc);
                        if (t.getDate() != null &&
                                (t.getDate().after(queryStartDate) || t.getDate().equals(queryStartDate)) &&
                                (t.getDate().before(queryEndDate) || t.getDate().equals(queryEndDate))) {
                            filteredList.add(t);
                        }
                    }

                    if (filteredList.isEmpty()) {
                        showLoading(false);
                        showError("No transactions found in the selected date range.");
                        return;
                    }

                    boolean isPdf = binding.chipFormatPdf.isChecked();
                    try {
                        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                        if (isPdf) {
                            String fileName = "BachatKhata_Report_" + timeStamp + ".pdf";
                            byte[] pdfBytes = generatePdf(filteredList);
                            saveFileToDownloads(fileName, "application/pdf", pdfBytes);
                        } else {
                            String fileName = "BachatKhata_Report_" + timeStamp + ".csv";
                            byte[] csvBytes = generateCsv(filteredList);
                            saveFileToDownloads(fileName, "text/csv", csvBytes);
                        }
                        showLoading(false);
                        showSuccess("Report exported to Downloads folder!");
                    } catch (Exception e) {
                        showLoading(false);
                        showError("Export failed: " + e.getMessage());
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    showError("Failed to retrieve transactions: " + e.getMessage());
                });
    }

    private byte[] generateCsv(List<Transaction> transactions) {
        StringBuilder csv = new StringBuilder();
        // CSV Header
        csv.append("Date,Type,Category,Amount,Currency,Note,Account\n");

        SimpleDateFormat csvDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        for (Transaction t : transactions) {
            String dateStr = t.getDate() != null ? csvDateFormat.format(t.getDate()) : "";
            String note = t.getNote() != null ? t.getNote().replace(",", " ") : ""; // remove commas to prevent breakage
            csv.append(String.format(Locale.US, "%s,%s,%s,%.2f,%s,%s,%s\n",
                    dateStr,
                    t.getType(),
                    t.getCategory(),
                    t.getAmount(),
                    t.getCurrency(),
                    note,
                    t.getAccount()
            ));
        }
        return csv.toString().getBytes();
    }

    private byte[] generatePdf(List<Transaction> transactions) throws Exception {
        PdfDocument document = new PdfDocument();
        int pageWidth = 595; // A4 width in pixels
        int pageHeight = 842; // A4 height in pixels

        // Paint setup
        Paint paint = new Paint();
        Paint titlePaint = new Paint();
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        titlePaint.setTextSize(18);
        titlePaint.setColor(Color.parseColor("#7C6FE0")); // Clay primary

        Paint headerPaint = new Paint();
        headerPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        headerPaint.setTextSize(11);
        headerPaint.setColor(Color.parseColor("#12101E"));

        Paint textPaint = new Paint();
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        textPaint.setTextSize(10);
        textPaint.setColor(Color.parseColor("#12101E"));

        Paint metaPaint = new Paint();
        metaPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        metaPaint.setTextSize(11);
        metaPaint.setColor(Color.parseColor("#6B6B8A"));

        // Compute metadata
        double totalIncome = 0;
        double totalExpense = 0;
        for (Transaction t : transactions) {
            if ("income".equalsIgnoreCase(t.getType())) {
                totalIncome += t.getAmount();
            } else {
                totalExpense += t.getAmount();
            }
        }
        double netSavings = totalIncome - totalExpense;

        int rowHeight = 25;
        int maxRowsPerPage = 22;
        int pageNumber = 1;
        int transactionIndex = 0;

        while (transactionIndex < transactions.size()) {
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
            PdfDocument.Page page = document.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            // Background
            canvas.drawColor(Color.parseColor("#FBFBFF"));

            // Header Frame/Clay Rounded look
            Paint cardBg = new Paint();
            cardBg.setColor(Color.parseColor("#F4F3FF"));
            canvas.drawRoundRect(20, 20, pageWidth - 20, 140, 16, 16, cardBg);

            // Title
            canvas.drawText("BachatKhata - Transaction Report", 35, 55, titlePaint);

            // Dates and KPI overview
            canvas.drawText(String.format("Period: %s - %s", displayDateFormat.format(startDate), displayDateFormat.format(endDate)), 35, 80, textPaint);
            canvas.drawText(String.format("Total Income: %s", CurrencyManager.getInstance().formatAmount(totalIncome)), 35, 105, metaPaint);
            canvas.drawText(String.format("Total Expense: %s", CurrencyManager.getInstance().formatAmount(totalExpense)), 210, 105, metaPaint);
            canvas.drawText(String.format("Net Savings: %s", CurrencyManager.getInstance().formatAmount(netSavings)), 385, 105, metaPaint);

            // Draw table headers
            int startY = 170;
            canvas.drawText("Date", 30, startY, headerPaint);
            canvas.drawText("Category", 130, startY, headerPaint);
            canvas.drawText("Note", 240, startY, headerPaint);
            canvas.drawText("Account", 380, startY, headerPaint);
            canvas.drawText("Amount", 500, startY, headerPaint);

            // Draw line below headers
            Paint linePaint = new Paint();
            linePaint.setColor(Color.parseColor("#E0DEFF"));
            linePaint.setStrokeWidth(1.5f);
            canvas.drawLine(20, startY + 5, pageWidth - 20, startY + 5, linePaint);

            int currentY = startY + 25;
            int rowsThisPage = 0;

            SimpleDateFormat pdfDateFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.US);

            while (transactionIndex < transactions.size() && rowsThisPage < maxRowsPerPage) {
                Transaction t = transactions.get(transactionIndex);

                // Row Alternating background
                if (rowsThisPage % 2 == 1) {
                    Paint altBg = new Paint();
                    altBg.setColor(Color.parseColor("#FDFDFD"));
                    canvas.drawRect(20, currentY - 15, pageWidth - 20, currentY + 10, altBg);
                }

                String dateStr = t.getDate() != null ? pdfDateFormat.format(t.getDate()) : "";
                canvas.drawText(dateStr, 30, currentY, textPaint);
                canvas.drawText(t.getCategory() != null ? t.getCategory() : "", 130, currentY, textPaint);
                
                String note = t.getNote() != null ? t.getNote() : "";
                if (note.length() > 22) {
                    note = note.substring(0, 20) + "..";
                }
                canvas.drawText(note, 240, currentY, textPaint);
                canvas.drawText(t.getAccount() != null ? t.getAccount() : "", 380, currentY, textPaint);

                // Style income/expense amount
                String sign = "income".equalsIgnoreCase(t.getType()) ? "+" : "-";
                int color = "income".equalsIgnoreCase(t.getType()) ? Color.parseColor("#3DAF85") : Color.parseColor("#E24B4A");
                Paint amountPaint = new Paint(textPaint);
                amountPaint.setColor(color);
                amountPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

                String amtStr = String.format("%s%s", sign, CurrencyManager.getInstance().formatAmount(t.getAmount()));
                canvas.drawText(amtStr, 500, currentY, amountPaint);

                // Row grid boundary line
                canvas.drawLine(20, currentY + 10, pageWidth - 20, currentY + 10, linePaint);

                currentY += rowHeight;
                transactionIndex++;
                rowsThisPage++;
            }

            // Footer
            canvas.drawText(String.format(Locale.US, "Page %d of %d", pageNumber, (int) Math.ceil((double) transactions.size() / maxRowsPerPage)), pageWidth / 2f - 30, pageHeight - 30, textPaint);
            canvas.drawText("Generated via BachatKhata", 30, pageHeight - 30, textPaint);

            document.finishPage(page);
            pageNumber++;
        }

        File tempFile = File.createTempFile("pdf_report", ".pdf", getCacheDir());
        FileOutputStream fos = new FileOutputStream(tempFile);
        document.writeTo(fos);
        document.close();
        fos.close();

        // Read bytes from temp file to return
        byte[] bytes = new byte[(int) tempFile.length()];
        java.io.FileInputStream fis = new java.io.FileInputStream(tempFile);
        fis.read(bytes);
        fis.close();
        tempFile.delete();

        return bytes;
    }

    private void saveFileToDownloads(String fileName, String mimeType, byte[] contentBytes) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ MediaStore approach (No permissions needed)
            ContentResolver resolver = getContentResolver();
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);
            if (uri == null) {
                throw new Exception("Failed to insert MediaStore download record.");
            }

            OutputStream os = resolver.openOutputStream(uri);
            if (os != null) {
                os.write(contentBytes);
                os.close();
            } else {
                throw new Exception("Failed to open output stream.");
            }
        } else {
            // Legacy approach
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs();
            }
            File file = new File(downloadsDir, fileName);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(contentBytes);
            fos.close();
        }
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
