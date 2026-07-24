package com.example.bachatkhata;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
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

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.util.Pair;

import com.example.bachatkhata.databinding.ActivityExportBinding;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ExportActivity extends BaseActivity {

    private ActivityExportBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;

    private Date startDate;
    private Date endDate;

    private enum Format { PDF, CSV, EXCEL }
    private Format selectedFormat = Format.PDF;

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
        updateFormatUI();
    }

    private void initDates() {
        Calendar cal = Calendar.getInstance();
        endDate = cal.getTime();

        // Defaults to start of current month
        cal.set(Calendar.DAY_OF_MONTH, 1);
        startDate = cal.getTime();

        updateDateRangeLabel();
    }

    private void updateDateRangeLabel() {
        String rangeStr = displayDateFormat.format(startDate) + " - " + displayDateFormat.format(endDate);
        binding.txtSelectedRange.setText(rangeStr);
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());

        binding.cardDateRange.setOnClickListener(v -> showDateRangePicker());

        binding.cardFormatPdf.setOnClickListener(v -> {
            selectedFormat = Format.PDF;
            updateFormatUI();
        });

        binding.cardFormatCsv.setOnClickListener(v -> {
            selectedFormat = Format.CSV;
            updateFormatUI();
        });

        binding.cardFormatExcel.setOnClickListener(v -> {
            selectedFormat = Format.EXCEL;
            updateFormatUI();
        });

        binding.btnExport.setOnClickListener(v -> performExport());

        binding.chipGroupPresets.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            applyPreset(checkedIds.get(0));
        });
    }

    private void applyPreset(int chipId) {
        Calendar cal = Calendar.getInstance();
        endDate = cal.getTime(); // today

        if (chipId == R.id.chipPresetToday) {
            // start stays today
        } else if (chipId == R.id.chipPresetWeek) {
            cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
        } else if (chipId == R.id.chipPreset30Days) {
            cal.add(Calendar.DAY_OF_YEAR, -29);
        } else if (chipId == R.id.chipPresetYear) {
            cal.set(Calendar.DAY_OF_YEAR, 1);
        } else { // chipPresetMonth (default)
            cal.set(Calendar.DAY_OF_MONTH, 1);
        }
        startDate = cal.getTime();
        updateDateRangeLabel();
    }

    private void updateFormatUI() {
        int activeBorder = ContextCompat.getColor(this, R.color.colorPrimary);
        int inactiveBorder = ContextCompat.getColor(this, R.color.colorCardBorder);

        binding.cardFormatPdf.setStrokeColor(selectedFormat == Format.PDF ? activeBorder : inactiveBorder);
        binding.cardFormatPdf.setStrokeWidth(selectedFormat == Format.PDF ? 4 : 2);

        binding.cardFormatCsv.setStrokeColor(selectedFormat == Format.CSV ? activeBorder : inactiveBorder);
        binding.cardFormatCsv.setStrokeWidth(selectedFormat == Format.CSV ? 4 : 2);

        binding.cardFormatExcel.setStrokeColor(selectedFormat == Format.EXCEL ? activeBorder : inactiveBorder);
        binding.cardFormatExcel.setStrokeWidth(selectedFormat == Format.EXCEL ? 4 : 2);

        // "Include charts" only applies to the PDF report
        binding.cardPdfOptions.setVisibility(selectedFormat == Format.PDF ? View.VISIBLE : View.GONE);
    }

    private void showDateRangePicker() {
        MaterialDatePicker<Pair<Long, Long>> dateRangePicker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText("Select Date Range")
                .setSelection(new Pair<>(startDate.getTime(), endDate.getTime()))
                .build();

        dateRangePicker.addOnPositiveButtonClickListener(selection -> {
            if (selection.first != null && selection.second != null) {
                startDate = new Date(selection.first);
                endDate = new Date(selection.second);
                binding.chipGroupPresets.clearCheck(); // custom range -> no preset highlighted
                updateDateRangeLabel();
            }
        });

        dateRangePicker.show(getSupportFragmentManager(), "DATE_RANGE_PICKER");
    }

    private void performExport() {
        if (mAuth.getCurrentUser() == null) return;
        showLoadingDialog();
        String uid = mAuth.getCurrentUser().getUid();

        // Query date boundaries
        Calendar cal = Calendar.getInstance();
        cal.setTime(startDate);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        Date queryStart = cal.getTime();

        cal.setTime(endDate);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        Date queryEnd = cal.getTime();

        mFirestore.collection("users").document(uid).collection("transactions")
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Transaction> list = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        Transaction t = Transaction.fromDocument(doc);
                        if (t.getDate() != null &&
                                (t.getDate().after(queryStart) || t.getDate().equals(queryStart)) &&
                                (t.getDate().before(queryEnd) || t.getDate().equals(queryEnd))) {
                            list.add(t);
                        }
                    }

                    if (list.isEmpty()) {
                        hideLoadingDialog();
                        showSnackbar("No transactions in selected range", "ERROR");
                        return;
                    }

                    try {
                        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                        String fileName;
                        String mimeType;
                        byte[] bytes;

                        switch (selectedFormat) {
                            case CSV:
                                fileName = "BachatKhata_" + timeStamp + ".csv";
                                mimeType = "text/csv";
                                bytes = generateCsv(list);
                                break;
                            case EXCEL:
                                fileName = "BachatKhata_" + timeStamp + ".xlsx";
                                mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                                bytes = generateXlsx(list);
                                break;
                            case PDF:
                            default:
                                fileName = "BachatKhata_" + timeStamp + ".pdf";
                                mimeType = "application/pdf";
                                bytes = generatePdf(list, binding.switchIncludeCharts.isChecked());
                                break;
                        }

                        File tempFile = new File(getCacheDir(), fileName);
                        FileOutputStream fos = new FileOutputStream(tempFile);
                        fos.write(bytes);
                        fos.close();

                        // Also drop a copy straight into the system Downloads folder
                        saveFileToDownloads(fileName, mimeType, bytes);

                        hideLoadingDialog();
                        shareFile(tempFile, mimeType);
                    } catch (Exception e) {
                        hideLoadingDialog();
                        showSnackbar("Export failed: " + e.getMessage(), "ERROR");
                    }
                })
                .addOnFailureListener(e -> {
                    hideLoadingDialog();
                    showSnackbar("Failed to query data: " + e.getMessage(), "ERROR");
                });
    }

    private byte[] generateCsv(List<Transaction> transactions) {
        StringBuilder csv = new StringBuilder();
        // Excel only detects UTF-8 in a .csv when the byte-order mark is present; without it
        // rupee signs and Devanagari notes open as mojibake.
        csv.append((char) 0xFEFF); // UTF-8 BOM
        appendCsvRow(csv, "Date", "Type", "Category", "Amount", "Currency", "Note", "Account");

        SimpleDateFormat csvDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        for (Transaction t : transactions) {
            String dateStr = t.getDate() != null ? csvDateFormat.format(t.getDate()) : "";
            appendCsvRow(csv,
                    dateStr,
                    t.getType(),
                    t.getCategory(),
                    String.format(Locale.US, "%.2f", t.getAmount()),
                    t.getCurrency(),
                    t.getNote(),
                    t.getAccount()
            );
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Writes one RFC-4180 record, terminated by CRLF. */
    private void appendCsvRow(StringBuilder out, String... fields) {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) out.append(',');
            out.append(escapeCsvField(fields[i]));
        }
        out.append("\r\n");
    }

    /**
     * RFC 4180 field escaping. Previously a note containing a comma shifted every column
     * after it, so the export silently corrupted itself on ordinary input.
     */
    private String escapeCsvField(String value) {
        if (value == null) return "";
        boolean mustQuote = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!mustQuote) return value;
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private byte[] generateXlsx(List<Transaction> transactions) throws Exception {
        String[] headers = {"Date", "Type", "Category", "Amount", "Currency", "Note", "Account"};
        SimpleDateFormat xlsxDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

        List<Object[]> rows = new ArrayList<>();
        for (Transaction t : transactions) {
            String dateStr = t.getDate() != null ? xlsxDateFormat.format(t.getDate()) : "";
            rows.add(new Object[]{
                    dateStr,
                    t.getType(),
                    t.getCategory(),
                    t.getAmount(),      // numeric cell
                    t.getCurrency(),
                    t.getNote() != null ? t.getNote() : "",
                    t.getAccount()
            });
        }
        return XlsxExporter.build("Transactions", headers, rows);
    }

    private byte[] generatePdf(List<Transaction> transactions, boolean includeCharts) throws Exception {
        PdfDocument document = new PdfDocument();
        int pageWidth = 595;  // A4 size
        int pageHeight = 842;

        Paint paint = new Paint();
        Paint titlePaint = new Paint();
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        titlePaint.setTextSize(18);
        titlePaint.setColor(Color.parseColor("#7C6FE0"));

        Paint headerPaint = new Paint();
        headerPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        headerPaint.setTextSize(11);
        headerPaint.setColor(Color.parseColor("#1A1A2E"));

        Paint textPaint = new Paint();
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        textPaint.setTextSize(10);
        textPaint.setColor(Color.parseColor("#1A1A2E"));

        Paint metaPaint = new Paint();
        metaPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        metaPaint.setTextSize(11);
        metaPaint.setColor(Color.parseColor("#6B6B8A"));

        // Totals
        double totalIncome = 0;
        double totalExpense = 0;
        Map<String, Double> categoryTotals = new HashMap<>();

        for (Transaction t : transactions) {
            double amt = t.getAmount();
            if ("income".equalsIgnoreCase(t.getType())) {
                totalIncome += amt;
            } else {
                totalExpense += amt;
                String cat = t.getCategory() != null ? t.getCategory() : "Other";
                categoryTotals.put(cat, categoryTotals.getOrDefault(cat, 0.0) + amt);
            }
        }
        double netSavings = totalIncome - totalExpense;

        int pageNumber = 1;
        int transactionIndex = 0;

        int rowHeight = 24;
        int startY = includeCharts ? 310 : 170;
        int maxRowsPerPage = includeCharts ? 16 : 22;

        while (transactionIndex < transactions.size()) {
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
            PdfDocument.Page page = document.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            // Background
            canvas.drawColor(Color.parseColor("#F5F3FF")); // Lavender white background

            // Header Frame
            Paint cardBg = new Paint();
            cardBg.setColor(Color.WHITE);
            canvas.drawRoundRect(20, 20, pageWidth - 20, 140, 20, 20, cardBg);

            // Header title & info
            canvas.drawText("BachatKhata Financial Statement", 40, 55, titlePaint);
            canvas.drawText("Period: " + displayDateFormat.format(startDate) + " - " + displayDateFormat.format(endDate), 40, 80, textPaint);

            canvas.drawText("Income: " + CurrencyManager.getInstance().formatAmount(totalIncome), 40, 110, metaPaint);
            canvas.drawText("Expense: " + CurrencyManager.getInstance().formatAmount(totalExpense), 210, 110, metaPaint);
            canvas.drawText("Savings: " + CurrencyManager.getInstance().formatAmount(netSavings), 380, 110, metaPaint);

            // Draw Charts if requested (Only on first page)
            if (includeCharts && pageNumber == 1) {
                // Draw white background card for chart summary
                canvas.drawRoundRect(20, 150, pageWidth - 20, 280, 20, 20, cardBg);

                // Simple Category Expense Summary Bar chart drawing
                Paint chartLabelPaint = new Paint(textPaint);
                chartLabelPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                canvas.drawText("Category Expenditure Summary", 40, 175, chartLabelPaint);

                int drawIndex = 0;
                int barY = 195;
                for (Map.Entry<String, Double> entry : categoryTotals.entrySet()) {
                    if (drawIndex >= 3) break; // Limit visual clutter to top 3
                    
                    String category = entry.getKey();
                    double val = entry.getValue();
                    float pct = totalExpense > 0 ? (float) (val / totalExpense) : 0;
                    
                    canvas.drawText(category + " (" + String.format(Locale.US, "%.1f%%", pct * 100) + ")", 40, barY, textPaint);
                    
                    // Draw Progress bar background
                    Paint barBgPaint = new Paint();
                    barBgPaint.setColor(Color.parseColor("#E0DEFF"));
                    canvas.drawRoundRect(180, barY - 10, pageWidth - 120, barY - 2, 4, 4, barBgPaint);

                    // Draw actual spent progress
                    Paint barFillPaint = new Paint();
                    barFillPaint.setColor(Color.parseColor("#7C6FE0"));
                    float fillWidth = 180 + (pct * (pageWidth - 300));
                    canvas.drawRoundRect(180, barY - 10, fillWidth, barY - 2, 4, 4, barFillPaint);

                    // Draw amount
                    canvas.drawText(CurrencyManager.getInstance().formatAmount(val), pageWidth - 105, barY, textPaint);

                    barY += 25;
                    drawIndex++;
                }
            }

            // Draw Table Headers
            canvas.drawText("Date", 35, startY, headerPaint);
            canvas.drawText("Category", 125, startY, headerPaint);
            canvas.drawText("Note", 230, startY, headerPaint);
            canvas.drawText("Account", 380, startY, headerPaint);
            canvas.drawText("Amount", 495, startY, headerPaint);

            Paint borderPaint = new Paint();
            borderPaint.setColor(Color.parseColor("#E0DEFF"));
            borderPaint.setStrokeWidth(1.5f);
            canvas.drawLine(20, startY + 6, pageWidth - 20, startY + 6, borderPaint);

            int currentY = startY + 24;
            int rowsThisPage = 0;

            SimpleDateFormat pdfDateFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.US);

            while (transactionIndex < transactions.size() && rowsThisPage < maxRowsPerPage) {
                Transaction t = transactions.get(transactionIndex);

                // Alternating backgrounds
                if (rowsThisPage % 2 == 1) {
                    Paint altBg = new Paint();
                    altBg.setColor(Color.parseColor("#FFFFFF"));
                    canvas.drawRoundRect(20, currentY - 15, pageWidth - 20, currentY + 9, 8, 8, altBg);
                }

                String dateStr = t.getDate() != null ? pdfDateFormat.format(t.getDate()) : "";
                canvas.drawText(dateStr, 35, currentY, textPaint);
                canvas.drawText(t.getCategory() != null ? t.getCategory() : "", 125, currentY, textPaint);

                String note = t.getNote() != null ? t.getNote() : "";
                if (note.length() > 22) note = note.substring(0, 20) + "..";
                canvas.drawText(note, 230, currentY, textPaint);
                canvas.drawText(t.getAccount() != null ? t.getAccount() : "", 380, currentY, textPaint);

                // Format values
                String sign = "income".equalsIgnoreCase(t.getType()) ? "+" : "-";
                int color = "income".equalsIgnoreCase(t.getType()) ? Color.parseColor("#3DAF85") : Color.parseColor("#E24B4A");
                Paint amountPaint = new Paint(textPaint);
                amountPaint.setColor(color);
                amountPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

                String amtStr = sign + CurrencyManager.getInstance().formatAmount(t.getAmount());
                canvas.drawText(amtStr, 495, currentY, amountPaint);

                canvas.drawLine(20, currentY + 9, pageWidth - 20, currentY + 9, borderPaint);

                currentY += rowHeight;
                transactionIndex++;
                rowsThisPage++;
            }

            // Footer
            int totalPages = (int) Math.ceil((double) transactions.size() / maxRowsPerPage);
            canvas.drawText("Page " + pageNumber + " of " + totalPages, pageWidth / 2f - 30, pageHeight - 30, textPaint);
            canvas.drawText("Generated via BachatKhata", 35, pageHeight - 30, textPaint);

            document.finishPage(page);
            pageNumber++;
            // Subsequent pages don't show charts, adjust start boundary & rows capacity
            startY = 170;
            maxRowsPerPage = 22;
        }

        File tempFile = File.createTempFile("pdf_report", ".pdf", getCacheDir());
        FileOutputStream fos = new FileOutputStream(tempFile);
        document.writeTo(fos);
        document.close();
        fos.close();

        byte[] bytes = new byte[(int) tempFile.length()];
        java.io.FileInputStream fis = new java.io.FileInputStream(tempFile);
        fis.read(bytes);
        fis.close();
        tempFile.delete();

        return bytes;
    }

    private void saveFileToDownloads(String fileName, String mimeType, byte[] contentBytes) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = getContentResolver();
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);
            if (uri != null) {
                OutputStream os = resolver.openOutputStream(uri);
                if (os != null) {
                    os.write(contentBytes);
                    os.close();
                }
            }
        } else {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadsDir.exists()) downloadsDir.mkdirs();
            File file = new File(downloadsDir, fileName);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(contentBytes);
            fos.close();
        }
    }

    private void shareFile(File file, String mimeType) {
        Uri fileUri = FileProvider.getUriForFile(this, "com.example.bachatkhata.fileprovider", file);
        
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType(mimeType);
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "BachatKhata Export Report");
        shareIntent.putExtra(Intent.EXTRA_TEXT, "Here is my financial statement summary.");
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        
        startActivity(Intent.createChooser(shareIntent, "Share Statement via"));
    }
}
