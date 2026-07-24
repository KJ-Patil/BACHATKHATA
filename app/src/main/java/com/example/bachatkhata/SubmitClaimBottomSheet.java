package com.example.bachatkhata;

import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bachatkhata.databinding.LayoutSubmitClaimBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SubmitClaimBottomSheet extends BottomSheetDialogFragment {

    private LayoutSubmitClaimBinding binding;
    private FirebaseFirestore mFirestore;
    private FirebaseAuth mAuth;
    private FirebaseStorage mStorage;

    private String groupId;
    private Uri selectedImageUri = null;
    private Runnable onDismissCallback;

    private final ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    binding.imgSelectedPreview.setImageURI(uri);
                    binding.imgSelectedPreview.setVisibility(View.VISIBLE);
                    binding.txtReceiptTitle.setText("Receipt Selected");
                    binding.txtReceiptSubtitle.setText("Tap to change receipt image");
                }
            }
    );

    public static SubmitClaimBottomSheet newInstance(String groupId) {
        SubmitClaimBottomSheet fragment = new SubmitClaimBottomSheet();
        Bundle args = new Bundle();
        args.putString("groupId", groupId);
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnDismissCallback(Runnable callback) {
        this.onDismissCallback = callback;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            groupId = getArguments().getString("groupId");
        }
        mFirestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        mStorage = FirebaseStorage.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = LayoutSubmitClaimBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (mAuth.getCurrentUser() != null) {
            loadCategories();
        }

        binding.cardUploadReceipt.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        binding.btnCancel.setOnClickListener(v -> dismiss());
        binding.btnSubmit.setOnClickListener(v -> submitClaim());
    }

    private void loadCategories() {
        String uid = mAuth.getCurrentUser().getUid();
        mFirestore.collection("users").document(uid).collection("categories")
                .whereEqualTo("type", "expense")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (binding == null) return;
                    binding.chipGroupCategory.removeAllViews();
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        if (Boolean.TRUE.equals(doc.getBoolean("archived"))) continue; // hidden from pickers
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

    private void submitClaim() {
        String amountStr = binding.etAmount.getText().toString().trim();
        String note = binding.etNote.getText().toString().trim();

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

        if (note.isEmpty()) {
            Toast.makeText(getContext(), "Please enter a claim description", Toast.LENGTH_SHORT).show();
            return;
        }

        int checkedId = binding.chipGroupCategory.getCheckedChipId();
        String selectedCategory = "Bills";
        if (checkedId != View.NO_ID) {
            Chip checkedChip = binding.chipGroupCategory.findViewById(checkedId);
            selectedCategory = checkedChip.getText().toString();
        }

        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        binding.btnSubmit.setEnabled(false);
        binding.btnSubmit.setText("Uploading...");

        String claimId = UUID.randomUUID().toString();

        final String finalCategory = selectedCategory;
        mFirestore.collection("users").document(uid).get()
                .addOnSuccessListener(userDoc -> {
                    String submitterName = "Member";
                    if (userDoc.exists()) {
                        String name = userDoc.getString("name");
                        if (name != null && !name.trim().isEmpty()) {
                            submitterName = name;
                        }
                    }

                    final String finalSubmitterName = submitterName;

                    if (selectedImageUri != null) {
                        uploadReceiptAndSaveClaim(claimId, uid, finalSubmitterName, amount, finalCategory, note);
                    } else {
                        saveClaimToFirestore(claimId, uid, finalSubmitterName, amount, finalCategory, note, null);
                    }
                })
                .addOnFailureListener(e -> {
                    binding.btnSubmit.setEnabled(true);
                    binding.btnSubmit.setText("Submit Expense Claim");
                    Toast.makeText(getContext(), "Failed to load profile details", Toast.LENGTH_SHORT).show();
                });
    }

    private void uploadReceiptAndSaveClaim(String claimId, String uid, String name, double amount, String category, String note) {
        StorageReference receiptRef = mStorage.getReference().child("users").child(uid).child("receipts").child(claimId + ".jpg");

        receiptRef.putFile(selectedImageUri)
                .addOnSuccessListener(taskSnapshot -> receiptRef.getDownloadUrl()
                        .addOnSuccessListener(uri -> saveClaimToFirestore(claimId, uid, name, amount, category, note, uri.toString()))
                        .addOnFailureListener(e -> {
                            binding.btnSubmit.setEnabled(true);
                            binding.btnSubmit.setText("Submit Expense Claim");
                            Toast.makeText(getContext(), "Failed to get download URL", Toast.LENGTH_SHORT).show();
                        }))
                .addOnFailureListener(e -> {
                    binding.btnSubmit.setEnabled(true);
                    binding.btnSubmit.setText("Submit Expense Claim");
                    Toast.makeText(getContext(), "Failed to upload receipt: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void saveClaimToFirestore(String claimId, String uid, String name, double amount, String category, String note, @Nullable String receiptUrl) {
        Map<String, Object> claimMap = new HashMap<>();
        claimMap.put("id", claimId);
        claimMap.put("submitterUid", uid);
        claimMap.put("submitterName", name);
        claimMap.put("amount", amount);
        claimMap.put("category", category);
        claimMap.put("note", note);
        claimMap.put("receiptUrl", receiptUrl);
        claimMap.put("status", "pending");
        claimMap.put("createdAt", Timestamp.now());
        claimMap.put("reviewedBy", null);
        claimMap.put("reviewedAt", null);

        mFirestore.collection("groups").document(groupId).collection("expense_claims")
                .document(claimId)
                .set(claimMap)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Claim submitted successfully!", Toast.LENGTH_SHORT).show();
                    if (onDismissCallback != null) {
                        onDismissCallback.run();
                    }
                    dismiss();
                })
                .addOnFailureListener(e -> {
                    binding.btnSubmit.setEnabled(true);
                    binding.btnSubmit.setText("Submit Expense Claim");
                    Toast.makeText(getContext(), "Save failed: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
