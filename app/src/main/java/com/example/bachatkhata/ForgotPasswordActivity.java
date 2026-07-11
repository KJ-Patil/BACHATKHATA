package com.example.bachatkhata;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Patterns;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.bachatkhata.databinding.ActivityForgotPasswordBinding;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;

public class ForgotPasswordActivity extends AppCompatActivity {

    private ActivityForgotPasswordBinding binding;
    private FirebaseAuth mAuth;

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        BaseActivity.applyEdgeToEdgeInsets(findViewById(android.R.id.content));

        mAuth = FirebaseAuth.getInstance();

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnSendResetLink.setOnClickListener(v -> handlePasswordReset());
    }

    private void handlePasswordReset() {
        String email = binding.etEmail.getText().toString().trim();

        if (email.isEmpty()) {
            binding.tilEmail.setError(getString(R.string.err_empty_email));
            return;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.setError(getString(R.string.err_invalid_email));
            return;
        } else {
            binding.tilEmail.setError(null);
        }

        showLoading(true);

        mAuth.sendPasswordResetEmail(email)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    showSuccess("Password reset link sent to your email!");
                    new Handler(Looper.getMainLooper()).postDelayed(this::finish, 2000);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    showError(e.getLocalizedMessage());
                });
    }

    private void showLoading(boolean isLoading) {
        binding.loaderOverlay.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }

    private void showSuccess(String message) {
        Snackbar snackbar = Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG);
        snackbar.setBackgroundTint(getResources().getColor(R.color.colorSecondary));
        snackbar.show();
    }

    private void showError(String message) {
        if (message == null || message.isEmpty()) message = "Failed to send reset link.";
        Snackbar snackbar = Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG);
        snackbar.setBackgroundTint(getResources().getColor(R.color.colorDanger));
        snackbar.show();
    }
}
