package com.example.bachatkhata;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bachatkhata.databinding.ActivityRegisterBinding;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;
    private FirebaseStorage mStorage;
    private Uri profileImageUri;
    private ActivityResultLauncher<String> getContentLauncher;

    private java.util.List<Country> countries;
    private Country selectedCountry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();
        mStorage = FirebaseStorage.getInstance();

        // Register Content Pick Launcher for profile picture
        getContentLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        profileImageUri = uri;
                        binding.imgProfile.setImageURI(uri);
                    }
                }
        );

        binding.cardProfileFrame.setOnClickListener(v -> getContentLauncher.launch("image/*"));
        binding.btnChoosePhoto.setOnClickListener(v -> getContentLauncher.launch("image/*"));
        binding.btnRegister.setOnClickListener(v -> handleRegister());
        binding.btnGoToLogin.setOnClickListener(v -> finish());

        // Country dial-code selector
        countries = Country.all();
        selectedCountry = countries.get(0); // India default
        updateCountryButton();
        binding.btnCountryCode.setOnClickListener(v -> showCountryPicker());
    }

    private void updateCountryButton() {
        binding.btnCountryCode.setText(selectedCountry.flag() + " " + selectedCountry.dialCode);
    }

    private void showCountryPicker() {
        String[] labels = new String[countries.size()];
        for (int i = 0; i < countries.size(); i++) {
            labels[i] = countries.get(i).displayLabel();
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Select country")
                .setItems(labels, (dialog, which) -> {
                    selectedCountry = countries.get(which);
                    updateCountryButton();
                })
                .show();
    }

    private void handleRegister() {
        String name = binding.etFullName.getText().toString().trim();
        String email = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();
        String confirmPassword = binding.etConfirmPassword.getText().toString().trim();

        if (name.isEmpty()) {
            binding.tilFullName.setError(getString(R.string.err_empty_name));
            return;
        } else {
            binding.tilFullName.setError(null);
        }

        if (email.isEmpty()) {
            binding.tilEmail.setError(getString(R.string.err_empty_email));
            return;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.setError(getString(R.string.err_invalid_email));
            return;
        } else {
            binding.tilEmail.setError(null);
        }

        if (password.isEmpty()) {
            binding.tilPassword.setError(getString(R.string.err_empty_password));
            return;
        } else if (password.length() < 6) {
            binding.tilPassword.setError(getString(R.string.err_short_password));
            return;
        } else {
            binding.tilPassword.setError(null);
        }

        if (!password.equals(confirmPassword)) {
            binding.tilConfirmPassword.setError(getString(R.string.err_password_mismatch));
            return;
        } else {
            binding.tilConfirmPassword.setError(null);
        }

        showLoading(true);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    if (user != null) {
                        uploadPhotoAndSaveUser(user, name);
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    showError(e.getLocalizedMessage());
                });
    }

    private void uploadPhotoAndSaveUser(FirebaseUser user, String name) {
        String uid = user.getUid();
        if (profileImageUri != null) {
            StorageReference profileRef = mStorage.getReference().child("users/" + uid + "/profile.jpg");
            profileRef.putFile(profileImageUri)
                    .addOnSuccessListener(taskSnapshot -> profileRef.getDownloadUrl()
                            .addOnSuccessListener(uri -> saveUserToFirestore(uid, name, uri.toString()))
                            .addOnFailureListener(e -> saveUserToFirestore(uid, name, "")))
                    .addOnFailureListener(e -> saveUserToFirestore(uid, name, ""));
        } else {
            saveUserToFirestore(uid, name, "");
        }
    }

    private void saveUserToFirestore(String uid, String name, String photoUrl) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("name", name);
        userData.put("email", mAuth.getCurrentUser().getEmail());
        userData.put("photoUrl", photoUrl);

        // Optional phone number with its country dial code.
        String phoneNumber = binding.etPhone.getText().toString().trim();
        if (!phoneNumber.isEmpty()) {
            userData.put("phone", selectedCountry.dialCode + " " + phoneNumber);
            userData.put("dialCode", selectedCountry.dialCode);
            userData.put("countryCode", selectedCountry.isoCode);
        }
        userData.put("currency", "INR");
        userData.put("currencySymbol", "₹");
        userData.put("pinHash", "");
        userData.put("biometricEnabled", false);
        userData.put("themeMode", "system");
        userData.put("createdAt", Timestamp.now());
        userData.put("onboardingComplete", false);

        mFirestore.collection("users").document(uid).set(userData)
                .addOnSuccessListener(aVoid -> {
                    // Seed defaults and navigate to onboarding
                    DefaultDataSeeder.seedDefaultData(uid,
                            aVoid2 -> {
                                showLoading(false);
                                startActivity(new Intent(RegisterActivity.this, OnboardingActivity.class));
                                finish();
                            },
                            e -> {
                                showLoading(false);
                                showError("Failed to seed default categories: " + e.getLocalizedMessage());
                            }
                    );
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    showError("Firestore registration error: " + e.getLocalizedMessage());
                });
    }

    private void showLoading(boolean isLoading) {
        binding.loaderOverlay.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        if (message == null || message.isEmpty()) message = "Registration failed.";
        Snackbar snackbar = Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG);
        snackbar.setBackgroundTint(getResources().getColor(R.color.colorDanger));
        snackbar.show();
    }
}
