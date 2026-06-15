package com.example.bachatkhata;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bachatkhata.databinding.LayoutAddSubscriptionBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AddSubscriptionBottomSheet extends BottomSheetDialogFragment {

    private LayoutAddSubscriptionBinding binding;
    private Date selectedDate = new Date();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.US);

    private FirebaseFirestore mFirestore;
    private FirebaseAuth mAuth;
    private Runnable onDismissCallback;

    public void setOnDismissCallback(Runnable callback) {
        this.onDismissCallback = callback;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mFirestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = LayoutAddSubscriptionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.txtDate.setText(dateFormat.format(selectedDate));
        
        binding.cardDatePicker.setOnClickListener(v -> {
            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Select Renewal Date")
                    .setSelection(selectedDate.getTime())
                    .build();

            datePicker.addOnPositiveButtonClickListener(selection -> {
                selectedDate = new Date(selection);
                binding.txtDate.setText(dateFormat.format(selectedDate));
            });

            datePicker.show(getChildFragmentManager(), "DATE_PICKER");
        });

        binding.btnCancel.setOnClickListener(v -> dismiss());
        binding.btnSave.setOnClickListener(v -> saveSubscription());
    }

    private void saveSubscription() {
        String name = binding.etName.getText().toString().trim();
        String amountStr = binding.etAmount.getText().toString().trim();

        if (name.isEmpty()) {
            Toast.makeText(getContext(), "Please enter a name", Toast.LENGTH_SHORT).show();
            return;
        }

        if (amountStr.isEmpty()) {
            Toast.makeText(getContext(), "Please enter an amount", Toast.LENGTH_SHORT).show();
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), "Invalid amount", Toast.LENGTH_SHORT).show();
            return;
        }

        if (amount <= 0) {
            Toast.makeText(getContext(), "Amount must be greater than zero", Toast.LENGTH_SHORT).show();
            return;
        }

        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        String id = mFirestore.collection("users").document(uid).collection("subscriptions").document().getId();

        Map<String, Object> sub = new HashMap<>();
        sub.put("id", id);
        sub.put("name", name);
        sub.put("amount", amount);
        sub.put("frequency", "Monthly");
        sub.put("nextRenewalDate", selectedDate);
        sub.put("isActive", true);
        sub.put("isAutoDetected", false);

        mFirestore.collection("users").document(uid).collection("subscriptions")
                .document(id)
                .set(sub)
                .addOnSuccessListener(aVoid -> {
                    if (getActivity() instanceof BaseActivity) {
                        ((BaseActivity) getActivity()).showSnackbar("Subscription Saved!", "SUCCESS");
                    } else {
                        Toast.makeText(getContext(), "Subscription Saved!", Toast.LENGTH_SHORT).show();
                    }
                    if (onDismissCallback != null) {
                        onDismissCallback.run();
                    }
                    dismiss();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Failed to save: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
