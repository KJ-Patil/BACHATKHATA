package com.example.bachatkhata;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bachatkhata.databinding.ActivityLoginBinding;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;
    private GoogleSignInClient mGoogleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();

        // 1. Configure Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // 2. Register ActivityResultLauncher for Google Sign-In
        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        handleGoogleSignInResult(task);
                    } else {
                        showError(getString(R.string.err_invalid_email));
                    }
                }
        );

        // 3. Set Up Button Click Listeners
        binding.btnSignIn.setOnClickListener(v -> handleEmailSignIn());
        binding.btnGoogleSignIn.setOnClickListener(v -> launchGoogleSignIn());
        binding.btnForgotPassword.setOnClickListener(v -> startActivity(new Intent(LoginActivity.this, ForgotPasswordActivity.class)));
        binding.btnGoToRegister.setOnClickListener(v -> startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));
    }

    private void handleEmailSignIn() {
        String email = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();

        if (email.isEmpty()) {
            binding.tilEmail.setError(getString(R.string.err_empty_email));
            return;
        } else {
            binding.tilEmail.setError(null);
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
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

        showLoading(true);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> checkUserStatusAndRoute())
                .addOnFailureListener(e -> {
                    showLoading(false);
                    showError(e.getLocalizedMessage());
                });
    }

    private void launchGoogleSignIn() {
        showLoading(true);
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        googleSignInLauncher.launch(signInIntent);
    }

    private void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            if (account != null) {
                firebaseAuthWithGoogle(account.getIdToken());
            } else {
                showLoading(false);
                showError("Google Sign-In Account is null");
            }
        } catch (ApiException e) {
            showLoading(false);
            showError("Google Sign-In failed: " + e.getMessage());
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null) {
                        // Check if user has a document in Firestore, otherwise initialize it
                        mFirestore.collection("users").document(user.getUid()).get()
                                .addOnSuccessListener(documentSnapshot -> {
                                    if (!documentSnapshot.exists()) {
                                        // Initialize new user document for Google users
                                        Map<String, Object> userData = new HashMap<>();
                                        userData.put("name", user.getDisplayName() != null ? user.getDisplayName() : "Google User");
                                        userData.put("email", user.getEmail());
                                        userData.put("photoUrl", user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : "");
                                        userData.put("currency", "INR");
                                        userData.put("currencySymbol", "₹");
                                        userData.put("themeMode", "system");
                                        userData.put("biometricEnabled", false);
                                        userData.put("pinHash", "");
                                        userData.put("onboardingComplete", false);
                                        userData.put("createdAt", com.google.firebase.Timestamp.now());

                                        mFirestore.collection("users").document(user.getUid()).set(userData)
                                                .addOnSuccessListener(aVoid -> checkUserStatusAndRoute())
                                                .addOnFailureListener(e -> {
                                                    showLoading(false);
                                                    showError("Failed to initialize Google profile: " + e.getMessage());
                                                });
                                    } else {
                                        checkUserStatusAndRoute();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    showLoading(false);
                                    showError("Firestore profile check failed: " + e.getMessage());
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    showError(e.getLocalizedMessage());
                });
    }

    private void checkUserStatusAndRoute() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            showLoading(false);
            return;
        }

        mFirestore.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    showLoading(false);
                    if (documentSnapshot.exists()) {
                        String pinHash = documentSnapshot.getString("pinHash");
                        Boolean onboardingComplete = documentSnapshot.getBoolean("onboardingComplete");

                        if (onboardingComplete != null && !onboardingComplete) {
                            startActivity(new Intent(LoginActivity.this, OnboardingActivity.class));
                        } else if (pinHash != null && !pinHash.trim().isEmpty()) {
                            Intent intent = new Intent(LoginActivity.this, PinSetupActivity.class);
                            intent.putExtra("mode", "VERIFY");
                            startActivity(intent);
                        } else {
                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        }
                    } else {
                        // Routing fallback
                        startActivity(new Intent(LoginActivity.this, OnboardingActivity.class));
                    }
                    finish();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    // Offline routing fallback
                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    finish();
                });
    }

    private void showLoading(boolean isLoading) {
        binding.loaderOverlay.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        if (message == null || message.isEmpty()) message = "Authentication failed.";
        Snackbar snackbar = Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG);
        snackbar.setBackgroundTint(getResources().getColor(R.color.colorDanger));
        snackbar.show();
    }
}
