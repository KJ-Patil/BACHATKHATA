package com.example.bachatkhata;

import android.app.AlertDialog;
import android.content.Intent;
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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bachatkhata.databinding.ActivityOrgDonationTrackerBinding;
import com.example.bachatkhata.databinding.ItemCategoryManageBinding;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OrgDonationTrackerActivity extends BaseActivity {

    private ActivityOrgDonationTrackerBinding binding;
    private FirebaseFirestore mFirestore;
    private FirebaseAuth mAuth;

    private String groupId;
    private String orgName = "BachatKhata NGO";

    private final List<Map<String, Object>> projectsList = new ArrayList<>();
    private final List<Map<String, Object>> donationsList = new ArrayList<>();
    private final Map<String, Double> categorySpentMap = new HashMap<>();

    private ProjectAdapter projectAdapter;
    private DonationAdapter donationAdapter;

    private ListenerRegistration projectsListener;
    private ListenerRegistration donationsListener;
    private ListenerRegistration transactionsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOrgDonationTrackerBinding.inflate(getLayoutInflater());
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
        seedDefaultProjectsIfNeeded();
        observeOrgDetails();
        observeExpensesAndProjects();
        observeDonations();
    }

    private void setupUI() {
        binding.btnBack.setOnClickListener(v -> finish());

        // Tabs toggle
        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = binding.tabLayout.getSelectedTabPosition();
                if (position == 0) {
                    binding.layoutProjects.setVisibility(View.VISIBLE);
                    binding.layoutDonations.setVisibility(View.GONE);
                } else {
                    binding.layoutProjects.setVisibility(View.GONE);
                    binding.layoutDonations.setVisibility(View.VISIBLE);
                }
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        // Setup Projects list
        binding.rvProjects.setLayoutManager(new LinearLayoutManager(this));
        projectAdapter = new ProjectAdapter();
        binding.rvProjects.setAdapter(projectAdapter);

        // Setup Donations list
        binding.rvDonations.setLayoutManager(new LinearLayoutManager(this));
        donationAdapter = new DonationAdapter();
        binding.rvDonations.setAdapter(donationAdapter);

        // Record donation FAB
        binding.fabAddDonation.setOnClickListener(v -> openRecordDonationSheet());

        // Project summary report button
        binding.btnExportSummary.setOnClickListener(v -> generateProjectSummaryReport());
    }

    private void observeOrgDetails() {
        mFirestore.collection("groups").document(groupId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String name = doc.getString("name");
                        if (name != null) {
                            orgName = name;
                            binding.txtTitle.setText(name);
                        }
                    }
                });
    }

    private void seedDefaultProjectsIfNeeded() {
        mFirestore.collection("groups").document(groupId).collection("projects")
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (snapshots == null || snapshots.isEmpty()) {
                        // Seed standard 3 projects
                        String[] names = {"Education Program", "Healthcare Campaign", "Community Welfare"};
                        double[] budgets = {50000.0, 75000.0, 30000.0};

                        for (int i = 0; i < names.length; i++) {
                            Map<String, Object> p = new HashMap<>();
                            p.put("name", names[i]);
                            p.put("budget", budgets[i]);
                            mFirestore.collection("groups").document(groupId).collection("projects").add(p);
                        }
                    }
                });
    }

    private void observeExpensesAndProjects() {
        // Query expenses to find budget utilization per category
        transactionsListener = mFirestore.collection("groups").document(groupId)
                .collection("group_transactions")
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;

                    categorySpentMap.clear();
                    if (value != null) {
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            String type = doc.getString("type");
                            String category = doc.getString("category");
                            Double amt = doc.getDouble("amount");

                            if ("expense".equalsIgnoreCase(type) && amt != null && category != null) {
                                categorySpentMap.put(category, categorySpentMap.getOrDefault(category, 0.0) + amt);
                            }
                        }
                    }

                    // Reload projects once expenses are mapped
                    loadProjects();
                });
    }

    private void loadProjects() {
        if (projectsListener != null) projectsListener.remove();

        projectsListener = mFirestore.collection("groups").document(groupId)
                .collection("projects")
                .addSnapshotListener((value, error) -> {
                    if (error != null || binding == null) return;

                    projectsList.clear();
                    if (value != null) {
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            Map<String, Object> p = doc.getData();
                            if (p != null) {
                                p.put("id", doc.getId());
                                projectsList.add(p);
                            }
                        }
                    }
                    projectAdapter.notifyDataSetChanged();
                });
    }

    private void observeDonations() {
        donationsListener = mFirestore.collection("groups").document(groupId)
                .collection("donations")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null || binding == null) return;

                    donationsList.clear();
                    if (value != null) {
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            Map<String, Object> d = doc.getData();
                            if (d != null) {
                                d.put("id", doc.getId());
                                donationsList.add(d);
                            }
                        }
                    }
                    donationAdapter.notifyDataSetChanged();
                });
    }

    private void openRecordDonationSheet() {
        ArrayList<String> names = new ArrayList<>();
        for (Map<String, Object> p : projectsList) {
            names.add((String) p.get("name"));
        }

        AddDonationBottomSheet sheet = AddDonationBottomSheet.newInstance(names);
        sheet.setOnDonationAddedListener((name, phone, amount, project, date) -> {
            recordDonation(name, phone, amount, project, date);
        });
        sheet.show(getSupportFragmentManager(), "AddDonationBottomSheet");
    }

    private void recordDonation(String name, String phone, double amount, String project, Date date) {
        showLoadingDialog();

        DocumentReference groupRef = mFirestore.collection("groups").document(groupId);

        mFirestore.runTransaction(transaction -> {
            DocumentSnapshot groupDoc = transaction.get(groupRef);
            Long counter = groupDoc.getLong("donationCounter");
            if (counter == null) {
                counter = 0L;
            }
            counter++;

            // Format receipt ORG-YYYY-NNNN
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            int year = cal.get(Calendar.YEAR);
            String receiptNo = String.format(Locale.US, "ORG-%d-%04d", year, counter);

            // Create donation doc
            DocumentReference donationRef = groupRef.collection("donations").document();
            Map<String, Object> d = new HashMap<>();
            d.put("donorName", name);
            d.put("donorPhone", phone);
            d.put("amount", amount);
            d.put("project", project);
            d.put("date", new Timestamp(date));
            d.put("receiptNo", receiptNo);
            d.put("createdAt", Timestamp.now());

            transaction.set(donationRef, d);
            transaction.update(groupRef, "donationCounter", counter);

            return d;
        }).addOnSuccessListener(donationMap -> {
            hideLoadingDialog();
            showSnackbar("Donation recorded successfully!", "SUCCESS");
            
            // Auto generate and share PDF receipt
            generatePdfReceipt(donationMap);
        }).addOnFailureListener(e -> {
            hideLoadingDialog();
            showSnackbar("Failed to record donation", "ERROR");
        });
    }

    private void generatePdfReceipt(Map<String, Object> donation) {
        String donorName = (String) donation.get("donorName");
        Double amount = (Double) donation.get("amount");
        String receiptNo = (String) donation.get("receiptNo");
        String project = (String) donation.get("project");
        Timestamp dateStamp = (Timestamp) donation.get("date");
        Date date = dateStamp != null ? dateStamp.toDate() : new Date();

        PdfDocument document = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        // Background
        canvas.drawColor(Color.parseColor("#FAF8FF"));

        // Frame Border
        Paint borderPaint = new Paint();
        borderPaint.setColor(Color.parseColor("#7C6FE0"));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3);
        canvas.drawRect(24, 24, 571, 818, borderPaint);

        // Primary headers
        Paint titlePaint = new Paint();
        titlePaint.setTextSize(24);
        titlePaint.setFakeBoldText(true);
        titlePaint.setColor(Color.parseColor("#7C6FE0"));

        Paint subTitlePaint = new Paint();
        subTitlePaint.setTextSize(14);
        subTitlePaint.setColor(Color.parseColor("#6B6B8A"));

        Paint labelPaint = new Paint();
        labelPaint.setTextSize(12);
        labelPaint.setFakeBoldText(true);
        labelPaint.setColor(Color.parseColor("#1A1A2E"));

        Paint textPaint = new Paint();
        textPaint.setTextSize(12);
        textPaint.setColor(Color.parseColor("#1A1A2E"));

        int y = 80;
        canvas.drawText(orgName, 50, y, titlePaint);
        canvas.drawText("Donation Receipt & Tax Certificate", 50, y + 25, subTitlePaint);

        canvas.drawLine(50, y + 40, 545, y + 40, borderPaint);

        y += 80;
        canvas.drawText("Receipt Number:", 50, y, labelPaint);
        canvas.drawText(receiptNo, 180, y, textPaint);

        y += 35;
        canvas.drawText("Date of Deposit:", 50, y, labelPaint);
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.US);
        canvas.drawText(sdf.format(date), 180, y, textPaint);

        y += 35;
        canvas.drawText("Received From:", 50, y, labelPaint);
        canvas.drawText(donorName, 180, y, textPaint);

        y += 35;
        canvas.drawText("Amount Donated:", 50, y, labelPaint);
        String formattedAmt = CurrencyManager.getInstance().formatAmount(amount);
        canvas.drawText(formattedAmt, 180, y, labelPaint);

        y += 35;
        canvas.drawText("Amount in Words:", 50, y, labelPaint);
        String words = NumberToWords.convert(amount.longValue()) + " Rupees Only";
        canvas.drawText(words, 180, y, textPaint);

        y += 35;
        canvas.drawText("Assigned Project:", 50, y, labelPaint);
        canvas.drawText(project, 180, y, textPaint);

        y += 60;
        Paint notePaint = new Paint(textPaint);
        notePaint.setTextSize(10);
        notePaint.setColor(Color.parseColor("#6B6B8A"));
        canvas.drawText("Note: This donation is eligible for tax benefits under Section 80G", 50, y, notePaint);
        canvas.drawText("of the Indian Income Tax Act, 1961. Thank you for your support!", 50, y + 16, notePaint);

        y += 120;
        canvas.drawLine(350, y, 520, y, textPaint);
        canvas.drawText("Authorised Signature", 370, y + 18, textPaint);
        canvas.drawText(orgName, 370, y + 36, subTitlePaint);

        document.finishPage(page);

        // Write PDF to cache file
        File pdfFile = new File(getExternalCacheDir(), "Receipt_" + receiptNo + ".pdf");
        try {
            document.writeTo(new FileOutputStream(pdfFile));
            document.close();

            sharePdfFile(pdfFile);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to build receipt PDF", Toast.LENGTH_SHORT).show();
        }
    }

    private void sharePdfFile(File file) {
        Uri fileUri = FileProvider.getUriForFile(this, "com.example.bachatkhata.fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM, fileUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Share Donation Receipt"));
    }

    private void generateProjectSummaryReport() {
        if (projectsList.isEmpty()) {
            Toast.makeText(this, "No projects to report", Toast.LENGTH_SHORT).show();
            return;
        }

        PdfDocument document = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        canvas.drawColor(Color.parseColor("#F4F3FF")); // Lavender BG

        Paint borderPaint = new Paint();
        borderPaint.setColor(Color.parseColor("#7C6FE0"));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2);
        canvas.drawRect(20, 20, 575, 822, borderPaint);

        Paint titlePaint = new Paint();
        titlePaint.setTextSize(22);
        titlePaint.setFakeBoldText(true);
        titlePaint.setColor(Color.parseColor("#7C6FE0"));

        Paint textPaint = new Paint();
        textPaint.setTextSize(11);
        textPaint.setColor(Color.parseColor("#1A1A2E"));

        Paint boldPaint = new Paint(textPaint);
        boldPaint.setFakeBoldText(true);

        int y = 70;
        canvas.drawText(orgName, 50, y, titlePaint);
        canvas.drawText("Project Status & Allocation Summary Report", 50, y + 25, textPaint);

        canvas.drawLine(50, y + 36, 545, y + 36, borderPaint);

        y += 80;
        // Draw Table Header
        canvas.drawText("Project Name", 50, y, boldPaint);
        canvas.drawText("Budget", 220, y, boldPaint);
        canvas.drawText("Funds Used", 330, y, boldPaint);
        canvas.drawText("Remaining", 440, y, boldPaint);

        canvas.drawLine(50, y + 6, 545, y + 6, borderPaint);
        y += 24;

        for (Map<String, Object> p : projectsList) {
            String name = (String) p.get("name");
            Double budget = (Double) p.get("budget");
            double spent = categorySpentMap.getOrDefault(name, 0.0);
            double remaining = budget != null ? budget - spent : 0.0;

            String budgetStr = budget != null ? CurrencyManager.getInstance().formatAmount(budget) : "₹0.00";
            String spentStr = CurrencyManager.getInstance().formatAmount(spent);
            String remStr = CurrencyManager.getInstance().formatAmount(remaining);

            canvas.drawText(name, 50, y, textPaint);
            canvas.drawText(budgetStr, 220, y, textPaint);
            canvas.drawText(spentStr, 330, y, textPaint);
            canvas.drawText(remStr, 440, y, textPaint);

            y += 20;
            canvas.drawLine(50, y - 10, 545, y - 10, borderPaint);
        }

        y += 60;
        canvas.drawText("Generated on: " + new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US).format(new Date()), 50, y, textPaint);
        canvas.drawText("This is an automatically compiled budget status report.", 50, y + 18, textPaint);

        document.finishPage(page);

        File reportFile = new File(getExternalCacheDir(), "Project_Summary_Report.pdf");
        try {
            document.writeTo(new FileOutputStream(reportFile));
            document.close();
            sharePdfFile(reportFile);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to compile report", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (projectsListener != null) projectsListener.remove();
        if (donationsListener != null) donationsListener.remove();
        if (transactionsListener != null) transactionsListener.remove();
    }

    // Projects Adapter
    private class ProjectAdapter extends RecyclerView.Adapter<ProjectAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemCategoryManageBinding binding = ItemCategoryManageBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new Holder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Map<String, Object> p = projectsList.get(position);
            holder.bind(p);
        }

        @Override
        public int getItemCount() {
            return projectsList.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            ItemCategoryManageBinding pBinding;

            Holder(ItemCategoryManageBinding binding) {
                super(binding.getRoot());
                pBinding = binding;
            }

            void bind(Map<String, Object> p) {
                String name = (String) p.get("name");
                Double budget = (Double) p.get("budget");
                double spent = categorySpentMap.getOrDefault(name, 0.0);

                pBinding.txtCategoryName.setText(name);

                double finalBudget = budget != null ? budget : 0.0;
                String budgetText = String.format(Locale.US, "Used: %s / %s",
                        CurrencyManager.getInstance().formatAmount(spent),
                        CurrencyManager.getInstance().formatAmount(finalBudget));

                pBinding.txtCategoryType.setText(budgetText);
                pBinding.txtCategoryType.setVisibility(View.VISIBLE);

                pBinding.btnDeleteCategory.setVisibility(View.GONE); // disable delete
            }
        }
    }

    // Donations Adapter
    private class DonationAdapter extends RecyclerView.Adapter<DonationAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemCategoryManageBinding binding = ItemCategoryManageBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new Holder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Map<String, Object> d = donationsList.get(position);
            holder.bind(d);
        }

        @Override
        public int getItemCount() {
            return donationsList.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            ItemCategoryManageBinding dBinding;

            Holder(ItemCategoryManageBinding binding) {
                super(binding.getRoot());
                dBinding = binding;
            }

            void bind(Map<String, Object> d) {
                String donor = (String) d.get("donorName");
                Double amount = (Double) d.get("amount");
                String receipt = (String) d.get("receiptNo");
                String project = (String) d.get("project");

                dBinding.txtCategoryName.setText(String.format(Locale.US, "%s (%s)", donor, receipt));
                dBinding.txtCategoryType.setText(String.format(Locale.US, "Fund: %s • %s",
                        project, CurrencyManager.getInstance().formatAmount(amount != null ? amount : 0.0)));
                dBinding.txtCategoryType.setVisibility(View.VISIBLE);

                dBinding.btnDeleteCategory.setImageResource(R.drawable.ic_camera); // Use camera/share/info icon for PDF generation
                dBinding.btnDeleteCategory.setContentDescription("Receipt Details");
                dBinding.btnDeleteCategory.setVisibility(View.VISIBLE);

                dBinding.btnDeleteCategory.setOnClickListener(v -> generatePdfReceipt(d));
            }
        }
    }
}
