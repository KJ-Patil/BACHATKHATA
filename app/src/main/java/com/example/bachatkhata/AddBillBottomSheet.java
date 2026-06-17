package com.example.bachatkhata;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.NumberPicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.OnFailureListener;
import com.example.bachatkhata.databinding.LayoutAddBillBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AddBillBottomSheet extends BottomSheetDialogFragment {

    private LayoutAddBillBinding binding;
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
        binding = LayoutAddBillBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Configure NumberPicker
        binding.pickerDueDay.setMinValue(1);
        binding.pickerDueDay.setMaxValue(31);
        binding.pickerDueDay.setValue(1);

        if (mAuth.getCurrentUser() != null) {
            loadCategories();
        }

        binding.btnCancel.setOnClickListener(v -> dismiss());
        binding.btnSave.setOnClickListener(v -> saveBill());
    }

    private void loadCategories() {
        String uid = mAuth.getCurrentUser().getUid();
        mFirestore.collection("users").document(uid).collection("categories")
                .whereEqualTo("type", "expense")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (binding == null) return;
                    if (queryDocumentSnapshots.isEmpty()) {
                        DefaultDataSeeder.seedDefaultData(uid,
                                aVoid -> loadCategories(),
                                e -> Toast.makeText(getContext(), "Failed to seed categories: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show()
                        );
                        return;
                    }
                    binding.chipGroupCategory.removeAllViews();
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        String catName = doc.getString("name");
                        if (catName != null) {
                            Chip chip = (Chip) LayoutInflater.from(getContext())
                                    .inflate(R.layout.item_category_chip, binding.chipGroupCategory, false);
                            chip.setText(catName);
                            chip.setId(View.generateViewId());
                            binding.chipGroupCategory.addView(chip);
                            if ("Bills".equalsIgnoreCase(catName)) {
                                chip.setChecked(true);
                            }
                        }
                    }
                });
    }

    private void saveBill() {
        String name = binding.etName.getText().toString().trim();
        String amountStr = binding.etAmount.getText().toString().trim();
        int dueDay = binding.pickerDueDay.getValue();

        if (name.isEmpty()) {
            Toast.makeText(getContext(), "Please enter a bill name", Toast.LENGTH_SHORT).show();
            return;
        }

        double amount = 0.0;
        if (!amountStr.isEmpty()) {
            try {
                amount = Double.parseDouble(amountStr);
            } catch (NumberFormatException e) {
                Toast.makeText(getContext(), "Invalid amount", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        int checkedChipId = binding.chipGroupCategory.getCheckedChipId();
        String category = "Bills";
        if (checkedChipId != View.NO_ID) {
            Chip checkedChip = binding.chipGroupCategory.findViewById(checkedChipId);
            category = checkedChip.getText().toString();
        }

        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        String id = mFirestore.collection("users").document(uid).collection("bills").document().getId();

        Map<String, Object> billMap = new HashMap<>();
        billMap.put("id", id);
        billMap.put("name", name);
        billMap.put("amount", amount);
        billMap.put("dueDay", dueDay);
        billMap.put("category", category);
        billMap.put("isRecurring", true);
        billMap.put("lastPaidDate", null);
        billMap.put("isActive", true);
        billMap.put("isDefault", false);

        mFirestore.collection("users").document(uid).collection("bills")
                .document(id)
                .set(billMap)
                .addOnSuccessListener(aVoid -> {
                    if (getActivity() instanceof BaseActivity) {
                        ((BaseActivity) getActivity()).showSnackbar("Bill reminder saved!", "SUCCESS");
                    } else {
                        Toast.makeText(getContext(), "Bill reminder saved!", Toast.LENGTH_SHORT).show();
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
