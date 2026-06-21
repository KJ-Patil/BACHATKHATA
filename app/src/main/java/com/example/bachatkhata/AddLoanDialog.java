package com.example.bachatkhata;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.bachatkhata.databinding.DialogAddLoanBinding;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AddLoanDialog extends DialogFragment {

    private DialogAddLoanBinding binding;
    private long selectedDateMillis = System.currentTimeMillis();
    private final SimpleDateFormat dateDisplayFmt = new SimpleDateFormat("dd MMM yyyy", Locale.US);

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_TITLE, 0);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        binding = DialogAddLoanBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        Window window = getDialog() != null ? getDialog().getWindow() : null;
        if (window != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.92f);
            // Cap height at 90% of screen so the ScrollView is scrollable and Save button is always reachable
            int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.90f);
            window.setLayout(width, maxHeight);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Default date = today
        binding.etStartDate.setText(dateDisplayFmt.format(new Date(selectedDateMillis)));

        binding.btnCloseDialog.setOnClickListener(v -> dismiss());
        binding.btnCancelLoan.setOnClickListener(v -> dismiss());

        // Date picker
        binding.etStartDate.setOnClickListener(v -> showDatePicker());
        binding.tilStartDate.setEndIconOnClickListener(v -> showDatePicker());

        binding.btnSaveLoan.setOnClickListener(v -> saveLoan());
    }

    private void showDatePicker() {
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Loan Start Date")
                .setSelection(selectedDateMillis)
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            selectedDateMillis = selection;
            // Adjust for timezone offset so displayed date matches selected
            Calendar utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
            utc.setTimeInMillis(selection);
            binding.etStartDate.setText(dateDisplayFmt.format(utc.getTime()));
        });

        picker.show(getChildFragmentManager(), "DatePicker");
    }

    private String getSelectedLender() {
        int checkedId = binding.chipGroupLender.getCheckedChipId();
        if (checkedId == binding.chipHdfc.getId()) return "HDFC Bank";
        if (checkedId == binding.chipSbi.getId()) return "SBI";
        if (checkedId == binding.chipIcici.getId()) return "ICICI Bank";
        if (checkedId == binding.chipAxis.getId()) return "Axis Bank";
        if (checkedId == binding.chipKotak.getId()) return "Kotak Bank";
        if (checkedId == binding.chipLic.getId()) return "LIC Housing";
        if (checkedId == binding.chipBajaj.getId()) return "Bajaj Finance";
        if (checkedId == binding.chipOther.getId()) return "Other";
        return "Other";
    }

    private void saveLoan() {
        String label = binding.etLoanLabel.getText() != null
                ? binding.etLoanLabel.getText().toString().trim() : "";
        String amountStr = binding.etLoanAmount.getText() != null
                ? binding.etLoanAmount.getText().toString().trim() : "";
        String rateStr = binding.etAnnualRate.getText() != null
                ? binding.etAnnualRate.getText().toString().trim() : "";
        String tenureStr = binding.etTenure.getText() != null
                ? binding.etTenure.getText().toString().trim() : "";
        String monthsPaidStr = binding.etMonthsPaid.getText() != null
                ? binding.etMonthsPaid.getText().toString().trim() : "";

        if (TextUtils.isEmpty(label)) {
            binding.tilLoanLabel.setError("Loan label is required");
            return;
        }
        binding.tilLoanLabel.setError(null);

        if (TextUtils.isEmpty(amountStr)) {
            binding.tilLoanAmount.setError("Enter loan amount");
            return;
        }
        binding.tilLoanAmount.setError(null);

        if (TextUtils.isEmpty(rateStr)) {
            binding.tilAnnualRate.setError("Enter annual rate");
            return;
        }
        binding.tilAnnualRate.setError(null);

        if (TextUtils.isEmpty(tenureStr)) {
            binding.tilTenure.setError("Enter tenure in months");
            return;
        }
        binding.tilTenure.setError(null);

        double principal, annualRate;
        int tenure, monthsPaid;
        try {
            principal = Double.parseDouble(amountStr);
            annualRate = Double.parseDouble(rateStr);
            tenure = Integer.parseInt(tenureStr);
            monthsPaid = TextUtils.isEmpty(monthsPaidStr) ? 0 : Integer.parseInt(monthsPaidStr);
        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), "Invalid number input", Toast.LENGTH_SHORT).show();
            return;
        }

        if (monthsPaid > tenure) monthsPaid = tenure;

        double emi = EmiCalculator.calculateEmi(principal, annualRate, tenure);
        String lender = getSelectedLender();

        // Build start date as Timestamp
        Calendar utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        utc.setTimeInMillis(selectedDateMillis);
        Date startDate = utc.getTime();

        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) {
            Toast.makeText(getContext(), "Not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("loanName", label);
        data.put("loanType", "Personal");   // default type kept for compatibility
        data.put("lender", lender);
        data.put("principal", principal);
        data.put("interestRate", annualRate);
        data.put("tenureMonths", tenure);
        data.put("monthsPaid", monthsPaid);
        data.put("emiAmount", emi);
        data.put("startDate", startDate);   // pass Date directly — same as AddEmiActivity
        data.put("createdAt", new Date());

        binding.btnSaveLoan.setEnabled(false);

        // Capture context before async so callbacks are safe even if fragment state changes
        android.content.Context ctx = requireContext().getApplicationContext();

        FirebaseFirestore.getInstance()
                .collection("users").document(uid).collection("emis")
                .add(data)
                .addOnSuccessListener(ref -> {
                    Toast.makeText(ctx, "Loan added successfully", Toast.LENGTH_SHORT).show();
                    scheduleReminder(ctx, ref.getId(), label, emi, startDate);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(ctx, "Failed to save: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });

        try {
            dismissAllowingStateLoss();
        } catch (Exception ignored) {
            dismiss();
        }
    }

    private void scheduleReminder(android.content.Context ctx, String emiId, String label, double emi, Date startDate) {
        try {
            androidx.work.Data inputData = new androidx.work.Data.Builder()
                    .putString("emiId", emiId)
                    .putString("loanName", label)
                    .putDouble("emiAmount", emi)
                    .build();

            Calendar start = Calendar.getInstance();
            start.setTime(startDate);
            int dayOfMonth = start.get(Calendar.DAY_OF_MONTH);

            Calendar nextDue = Calendar.getInstance();
            nextDue.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            nextDue.set(Calendar.HOUR_OF_DAY, 8);
            nextDue.set(Calendar.MINUTE, 0);
            nextDue.set(Calendar.SECOND, 0);
            nextDue.set(Calendar.MILLISECOND, 0);
            if (nextDue.before(Calendar.getInstance())) {
                nextDue.add(Calendar.MONTH, 1);
            }

            long delay = nextDue.getTimeInMillis() - System.currentTimeMillis();
            if (delay < 0) delay = 0;

            androidx.work.OneTimeWorkRequest reminder = new androidx.work.OneTimeWorkRequest.Builder(EmiReminderWorker.class)
                    .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .setInputData(inputData)
                    .addTag("emi_" + emiId)
                    .build();

            androidx.work.WorkManager.getInstance(ctx).enqueue(reminder);
        } catch (Exception ignored) {
            // Non-critical: if scheduling fails, the save already succeeded
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
