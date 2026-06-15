package com.example.bachatkhata;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.example.bachatkhata.databinding.ActivityAddEmiBinding;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class AddEmiActivity extends BaseActivity {

    private ActivityAddEmiBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;

    private String selectedLoanType = "Personal";
    private Date startDate = new Date();
    private double calculatedEmi = 0.0;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddEmiBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();

        setupTypeSelectors();
        setupDatePicker();
        setupListeners();

        // Select Personal type by default
        selectLoanType("Personal");
    }

    private void setupTypeSelectors() {
        binding.cardTypeHome.setOnClickListener(v -> selectLoanType("Home"));
        binding.cardTypeCar.setOnClickListener(v -> selectLoanType("Car"));
        binding.cardTypePersonal.setOnClickListener(v -> selectLoanType("Personal"));
        binding.cardTypeCard.setOnClickListener(v -> selectLoanType("Credit Card"));
    }

    private void selectLoanType(String type) {
        selectedLoanType = type;

        // Reset backgrounds and stroke colors
        binding.cardTypeHome.setStrokeColor(Color.parseColor("#E0E0E0"));
        binding.cardTypeCar.setStrokeColor(Color.parseColor("#E0E0E0"));
        binding.cardTypePersonal.setStrokeColor(Color.parseColor("#E0E0E0"));
        binding.cardTypeCard.setStrokeColor(Color.parseColor("#E0E0E0"));

        binding.cardTypeHome.setCardBackgroundColor(Color.WHITE);
        binding.cardTypeCar.setCardBackgroundColor(Color.WHITE);
        binding.cardTypePersonal.setCardBackgroundColor(Color.WHITE);
        binding.cardTypeCard.setCardBackgroundColor(Color.WHITE);

        // Highlight selected
        com.google.android.material.card.MaterialCardView selectedCard = null;
        if ("Home".equals(type)) {
            selectedCard = binding.cardTypeHome;
        } else if ("Car".equals(type)) {
            selectedCard = binding.cardTypeCar;
        } else if ("Personal".equals(type)) {
            selectedCard = binding.cardTypePersonal;
        } else if ("Credit Card".equals(type)) {
            selectedCard = binding.cardTypeCard;
        }

        if (selectedCard != null) {
            selectedCard.setStrokeColor(Color.parseColor("#7C6FE0")); // colorPrimary
            selectedCard.setCardBackgroundColor(Color.parseColor("#EEECFA")); // soft purple
        }
    }

    private void setupDatePicker() {
        binding.txtDate.setText(dateFormat.format(startDate));
        binding.cardDatePicker.setOnClickListener(v -> {
            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Select Loan Start Date")
                    .setSelection(startDate.getTime())
                    .build();

            datePicker.addOnPositiveButtonClickListener(selection -> {
                startDate = new Date(selection);
                binding.txtDate.setText(dateFormat.format(startDate));
            });

            datePicker.show(getSupportFragmentManager(), "DATE_PICKER");
        });
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnCalculate.setOnClickListener(v -> performCalculation());
        binding.btnSaveEmi.setOnClickListener(v -> saveLoan());
    }

    private boolean validateInputs() {
        String name = binding.etLoanName.getText().toString().trim();
        String principalStr = binding.etPrincipal.getText().toString().trim();
        String rateStr = binding.etRate.getText().toString().trim();
        String tenureStr = binding.etTenure.getText().toString().trim();

        if (name.isEmpty()) {
            showSnackbar("Please enter a loan name.", "ERROR");
            return false;
        }
        if (principalStr.isEmpty() || Double.parseDouble(principalStr) <= 0) {
            showSnackbar("Please enter a valid principal amount.", "ERROR");
            return false;
        }
        if (rateStr.isEmpty() || Double.parseDouble(rateStr) < 0) {
            showSnackbar("Please enter a valid interest rate.", "ERROR");
            return false;
        }
        if (tenureStr.isEmpty() || Integer.parseInt(tenureStr) <= 0) {
            showSnackbar("Please enter a valid tenure in months.", "ERROR");
            return false;
        }
        return true;
    }

    private void performCalculation() {
        if (!validateInputs()) return;

        double principal = Double.parseDouble(binding.etPrincipal.getText().toString().trim());
        double rate = Double.parseDouble(binding.etRate.getText().toString().trim());
        int tenure = Integer.parseInt(binding.etTenure.getText().toString().trim());

        calculatedEmi = EmiCalculator.calculateEmi(principal, rate, tenure);

        binding.txtResultEmi.setText(CurrencyManager.getInstance().formatAmount(calculatedEmi));
        binding.cardResult.setVisibility(View.VISIBLE);
        AnimationHelper.buttonPressAnimation(binding.cardResult);
    }

    private void saveLoan() {
        if (!validateInputs()) return;

        if (calculatedEmi <= 0) {
            // Force calculation
            double principal = Double.parseDouble(binding.etPrincipal.getText().toString().trim());
            double rate = Double.parseDouble(binding.etRate.getText().toString().trim());
            int tenure = Integer.parseInt(binding.etTenure.getText().toString().trim());
            calculatedEmi = EmiCalculator.calculateEmi(principal, rate, tenure);
        }

        showLoadingDialog();

        String name = binding.etLoanName.getText().toString().trim();
        double principal = Double.parseDouble(binding.etPrincipal.getText().toString().trim());
        double rate = Double.parseDouble(binding.etRate.getText().toString().trim());
        int tenure = Integer.parseInt(binding.etTenure.getText().toString().trim());

        String uid = mAuth.getCurrentUser().getUid();
        String id = mFirestore.collection("users").document(uid).collection("emis").document().getId();

        Map<String, Object> emiMap = new HashMap<>();
        emiMap.put("id", id);
        emiMap.put("loanName", name);
        emiMap.put("loanType", selectedLoanType);
        emiMap.put("principal", principal);
        emiMap.put("interestRate", rate);
        emiMap.put("tenureMonths", tenure);
        emiMap.put("startDate", startDate);
        emiMap.put("emiAmount", calculatedEmi);
        emiMap.put("createdAt", new Date());

        mFirestore.collection("users").document(uid).collection("emis")
                .document(id)
                .set(emiMap)
                .addOnSuccessListener(aVoid -> {
                    // Schedule reminders for the first 12 months
                    scheduleEmiReminders(name, calculatedEmi);
                    hideLoadingDialog();
                    showSnackbar("Loan details saved successfully!", "SUCCESS");
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::finish, 1200);
                })
                .addOnFailureListener(e -> {
                    hideLoadingDialog();
                    showSnackbar("Failed to save loan: " + e.getLocalizedMessage(), "ERROR");
                });
    }

    private void scheduleEmiReminders(String loanName, double emiAmount) {
        WorkManager workManager = WorkManager.getInstance(this);

        for (int i = 0; i < 12; i++) {
            Calendar dueCal = Calendar.getInstance();
            dueCal.setTime(startDate);
            dueCal.add(Calendar.MONTH, i);
            dueCal.add(Calendar.DAY_OF_YEAR, -2); // Alert 2 days before due date
            dueCal.set(Calendar.HOUR_OF_DAY, 9);  // Send alert at 9:00 AM
            dueCal.set(Calendar.MINUTE, 0);
            dueCal.set(Calendar.SECOND, 0);
            dueCal.set(Calendar.MILLISECOND, 0);

            long delayMs = dueCal.getTimeInMillis() - System.currentTimeMillis();
            if (delayMs > 0) {
                OneTimeWorkRequest reminderRequest = new OneTimeWorkRequest.Builder(EmiReminderWorker.class)
                        .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                        .setInputData(new Data.Builder()
                                .putString("loanName", loanName)
                                .putDouble("emiAmount", emiAmount)
                                .build())
                        .build();

                workManager.enqueue(reminderRequest);
            }
        }
    }
}
