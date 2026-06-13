package com.example.bachatkhata;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.bachatkhata.databinding.FragmentProfileBinding;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();

        setupListeners();
        loadProfileInfo();
    }

    private void setupListeners() {
        binding.rowEditProfile.setOnClickListener(v -> showEditNameDialog());

        binding.rowCurrency.setOnClickListener(v -> selectBaseCurrency());

        binding.rowSetupPin.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), PinSetupActivity.class);
            intent.putExtra("mode", "SETUP");
            startActivity(intent);
        });

        binding.switchBiometrics.setOnCheckedChangeListener((buttonView, isChecked) -> updateBiometricSetting(isChecked));

        binding.rowManageCategories.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), CategoryManageActivity.class);
            startActivity(intent);
        });

        binding.rowExport.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), ExportActivity.class);
            startActivity(intent);
        });

        binding.btnLogout.setOnClickListener(v -> logoutUser());
    }

    private void loadProfileInfo() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        binding.txtUserEmail.setText(mAuth.getCurrentUser().getEmail());

        mFirestore.collection("users").document(uid).addSnapshotListener((documentSnapshot, error) -> {
            if (error != null) return;

            if (documentSnapshot != null && documentSnapshot.exists()) {
                String name = documentSnapshot.getString("name");
                Boolean bioEnabled = documentSnapshot.getBoolean("biometricEnabled");
                String currencyCode = documentSnapshot.getString("currency");
                String currencySymbol = documentSnapshot.getString("currencySymbol");

                if (binding != null) {
                    if (name != null && !name.trim().isEmpty()) {
                        binding.txtUserName.setText(name);
                        binding.txtAvatarLetter.setText(name.substring(0, 1).toUpperCase(Locale.US));
                    } else {
                        binding.txtUserName.setText("User");
                        binding.txtAvatarLetter.setText("U");
                    }

                    if (bioEnabled != null) {
                        // Temporarily remove listener to prevent trigger on load
                        binding.switchBiometrics.setOnCheckedChangeListener(null);
                        binding.switchBiometrics.setChecked(bioEnabled);
                        binding.switchBiometrics.setOnCheckedChangeListener((buttonView, isChecked) -> updateBiometricSetting(isChecked));
                    }

                    if (currencyCode != null && currencySymbol != null) {
                        binding.txtSelectedCurrency.setText(String.format("%s (%s)", currencyCode, currencySymbol));
                    } else {
                        binding.txtSelectedCurrency.setText("INR (₹)");
                    }
                }
            }
        });
    }

    private void showEditNameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Edit Profile Name");

        FrameLayout container = new FrameLayout(getContext());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = params.rightMargin = params.topMargin = params.bottomMargin = 48;

        EditText input = new EditText(getContext());
        input.setHint("Enter your name");
        input.setText(binding.txtUserName.getText().toString());
        input.setLayoutParams(params);
        container.addView(input);
        builder.setView(container);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty()) {
                updateProfileName(newName);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void updateProfileName(String newName) {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        mFirestore.collection("users").document(uid)
                .update("name", newName)
                .addOnSuccessListener(aVoid -> {
                    if (getView() != null) {
                        Snackbar.make(getView(), "Profile updated!", Snackbar.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    if (getView() != null) {
                        Snackbar.make(getView(), "Update failed: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    private void selectBaseCurrency() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();
        String currentCode = CurrencyManager.getInstance().getCurrentCurrencyCode();

        CurrencyPickerBottomSheet.newInstance(currentCode, currency -> {
            CurrencyManager.getInstance().saveToFirestore(uid, currency.code, currency.symbol, () -> {
                if (binding != null) {
                    binding.txtSelectedCurrency.setText(String.format("%s (%s)", currency.code, currency.symbol));
                    if (getView() != null) {
                        Snackbar.make(getView(), "Base currency updated!", Snackbar.LENGTH_SHORT).show();
                    }
                }
            });
        }).show(getParentFragmentManager(), "CURRENCY_PICKER");
    }

    private void updateBiometricSetting(boolean enabled) {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        mFirestore.collection("users").document(uid)
                .update("biometricEnabled", enabled)
                .addOnSuccessListener(aVoid -> {
                    if (getView() != null) {
                        String msg = enabled ? "Biometric authentication enabled" : "Biometric authentication disabled";
                        Snackbar.make(getView(), msg, Snackbar.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    if (binding != null) {
                        // Revert check state on failure
                        binding.switchBiometrics.setOnCheckedChangeListener(null);
                        binding.switchBiometrics.setChecked(!enabled);
                        binding.switchBiometrics.setOnCheckedChangeListener((buttonView, isChecked) -> updateBiometricSetting(isChecked));
                    }
                    if (getView() != null) {
                        Snackbar.make(getView(), "Failed to update biometric: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    private void logoutUser() {
        mAuth.signOut();
        Intent intent = new Intent(getContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
