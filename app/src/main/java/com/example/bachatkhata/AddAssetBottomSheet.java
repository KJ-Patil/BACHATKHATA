package com.example.bachatkhata;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bachatkhata.databinding.LayoutAddAssetBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class AddAssetBottomSheet extends BottomSheetDialogFragment {

    private LayoutAddAssetBinding binding;
    private FirebaseFirestore mFirestore;
    private FirebaseAuth mAuth;
    private OnAssetAddedListener listener;

    public interface OnAssetAddedListener {
        void onAssetAdded();
    }

    public void setOnAssetAddedListener(OnAssetAddedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = LayoutAddAssetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mFirestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        setupGoldCalculator();
        setupListeners();
    }

    private void setupGoldCalculator() {
        binding.spinnerAssetType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedType = parent.getItemAtPosition(position).toString();
                if ("Gold".equalsIgnoreCase(selectedType)) {
                    binding.cardGoldHelper.setVisibility(View.VISIBLE);
                } else {
                    binding.cardGoldHelper.setVisibility(View.GONE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                binding.cardGoldHelper.setVisibility(View.GONE);
            }
        });

        TextWatcher goldWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                calculateGoldValuation();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        binding.etGoldWeight.addTextChangedListener(goldWatcher);
        binding.etGoldRate.addTextChangedListener(goldWatcher);
    }

    private void calculateGoldValuation() {
        String weightStr = binding.etGoldWeight.getText().toString().trim();
        String rateStr = binding.etGoldRate.getText().toString().trim();

        if (!weightStr.isEmpty() && !rateStr.isEmpty()) {
            try {
                double weight = Double.parseDouble(weightStr);
                double rate = Double.parseDouble(rateStr);
                double valuation = weight * rate;
                
                binding.txtGoldValuationResult.setText(String.format(Locale.US, "Calculated value: ₹%.2f", valuation));
                binding.etAssetAmount.setText(String.format(Locale.US, "%.2f", valuation));
            } catch (NumberFormatException e) {
                binding.txtGoldValuationResult.setText("Calculated value: ₹0.00");
            }
        } else {
            binding.txtGoldValuationResult.setText("Calculated value: ₹0.00");
        }
    }

    private void setupListeners() {
        binding.btnSaveAsset.setOnClickListener(v -> saveAsset());
    }

    private void saveAsset() {
        if (mAuth.getCurrentUser() == null) return;

        String name = binding.etAssetName.getText().toString().trim();
        String amountStr = binding.etAssetAmount.getText().toString().trim();
        String type = binding.spinnerAssetType.getSelectedItem().toString();
        String notes = binding.etAssetNotes.getText().toString().trim();

        if (name.isEmpty()) {
            binding.tilAssetName.setError("Name cannot be empty");
            return;
        }
        binding.tilAssetName.setError(null);

        if (amountStr.isEmpty()) {
            binding.tilAssetAmount.setError("Amount cannot be empty");
            return;
        }
        
        double amount;
        try {
            amount = Double.parseDouble(amountStr);
            if (amount <= 0) {
                binding.tilAssetAmount.setError("Amount must be greater than zero");
                return;
            }
        } catch (NumberFormatException e) {
            binding.tilAssetAmount.setError("Invalid amount");
            return;
        }
        binding.tilAssetAmount.setError(null);

        String uid = mAuth.getCurrentUser().getUid();
        String assetId = UUID.randomUUID().toString();

        Map<String, Object> asset = new HashMap<>();
        asset.put("id", assetId);
        asset.put("name", name);
        asset.put("type", type);
        asset.put("amount", amount);
        asset.put("notes", notes);
        asset.put("createdAt", Timestamp.now());

        mFirestore.collection("users").document(uid).collection("assets").document(assetId)
                .set(asset)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Asset saved successfully", Toast.LENGTH_SHORT).show();
                    if (listener != null) {
                        listener.onAssetAdded();
                    }
                    dismiss();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Failed to save asset: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
