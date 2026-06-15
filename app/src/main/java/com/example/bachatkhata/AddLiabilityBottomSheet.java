package com.example.bachatkhata;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bachatkhata.databinding.LayoutAddLiabilityBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AddLiabilityBottomSheet extends BottomSheetDialogFragment {

    private LayoutAddLiabilityBinding binding;
    private FirebaseFirestore mFirestore;
    private FirebaseAuth mAuth;
    private OnLiabilityAddedListener listener;

    public interface OnLiabilityAddedListener {
        void onLiabilityAdded();
    }

    public void setOnLiabilityAddedListener(OnLiabilityAddedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = LayoutAddLiabilityBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mFirestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        binding.btnSaveLiability.setOnClickListener(v -> saveLiability());
    }

    private void saveLiability() {
        if (mAuth.getCurrentUser() == null) return;

        String name = binding.etLiabilityName.getText().toString().trim();
        String amountStr = binding.etLiabilityAmount.getText().toString().trim();
        String type = binding.spinnerLiabilityType.getSelectedItem().toString();
        String notes = binding.etLiabilityNotes.getText().toString().trim();

        if (name.isEmpty()) {
            binding.tilLiabilityName.setError("Name cannot be empty");
            return;
        }
        binding.tilLiabilityName.setError(null);

        if (amountStr.isEmpty()) {
            binding.tilLiabilityAmount.setError("Amount cannot be empty");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
            if (amount <= 0) {
                binding.tilLiabilityAmount.setError("Amount must be greater than zero");
                return;
            }
        } catch (NumberFormatException e) {
            binding.tilLiabilityAmount.setError("Invalid amount");
            return;
        }
        binding.tilLiabilityAmount.setError(null);

        String uid = mAuth.getCurrentUser().getUid();
        String liabilityId = UUID.randomUUID().toString();

        Map<String, Object> liability = new HashMap<>();
        liability.put("id", liabilityId);
        liability.put("name", name);
        liability.put("type", type);
        liability.put("amount", amount);
        liability.put("notes", notes);
        liability.put("createdAt", Timestamp.now());

        mFirestore.collection("users").document(uid).collection("liabilities").document(liabilityId)
                .set(liability)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Liability saved successfully", Toast.LENGTH_SHORT).show();
                    if (listener != null) {
                        listener.onLiabilityAdded();
                    }
                    dismiss();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Failed to save liability: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
