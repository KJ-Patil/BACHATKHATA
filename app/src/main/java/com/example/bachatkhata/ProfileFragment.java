package com.example.bachatkhata;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.bachatkhata.databinding.FragmentProfileBinding;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;
    private ActivityResultLauncher<String> pickImageLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        uploadProfileImage(uri);
                    }
                }
        );
    }

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
        loadTimeoutInfo();
    }

    private void setupListeners() {
        binding.rowEditProfile.setOnClickListener(v -> showEditNameDialog());
        binding.cardProfileImage.setOnClickListener(v -> selectProfilePhoto());
        binding.rowChangePassword.setOnClickListener(v -> showChangePasswordDialog());
        binding.rowCurrency.setOnClickListener(v -> selectBaseCurrency());
        binding.rowTheme.setOnClickListener(v -> showThemeSelectionDialog());
        binding.rowLockTimeout.setOnClickListener(v -> showLockTimeoutDialog());
        binding.rowClearData.setOnClickListener(v -> confirmClearData());

        binding.rowSetupPin.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), PinSetupActivity.class);
            intent.putExtra("mode", "SETUP");
            startActivity(intent);
        });

        binding.switchBiometrics.setOnCheckedChangeListener((buttonView, isChecked) -> updateBiometricSetting(isChecked));
        binding.switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> updateNotificationsSetting(isChecked));

        binding.rowManageCategories.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), CategoryManageActivity.class);
            startActivity(intent);
        });

        binding.rowCustomerLedger.setOnClickListener(v -> {
            androidx.navigation.Navigation.findNavController(v).navigate(R.id.navigation_customer_ledger);
        });

        binding.rowSubscriptions.setOnClickListener(v -> {
            androidx.navigation.Navigation.findNavController(v).navigate(R.id.navigation_subscriptions);
        });

        binding.rowEmiTracker.setOnClickListener(v -> {
            androidx.navigation.Navigation.findNavController(v).navigate(R.id.navigation_emi_tracker);
        });

        binding.rowBillCalendar.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), BillCalendarActivity.class);
            startActivity(intent);
        });

        binding.rowFamilyWallet.setOnClickListener(v -> {
            androidx.navigation.Navigation.findNavController(v).navigate(R.id.navigation_family_wallet);
        });

        binding.rowAchievements.setOnClickListener(v -> {
            androidx.navigation.Navigation.findNavController(v).navigate(R.id.navigation_achievements);
        });

        binding.rowHealthScore.setOnClickListener(v -> {
            androidx.navigation.Navigation.findNavController(v).navigate(R.id.navigation_health_score);
        });

        binding.rowNetWorth.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), NetWorthActivity.class);
            startActivity(intent);
        });

        binding.rowRoundUpSettings.setOnClickListener(v -> {
            androidx.navigation.Navigation.findNavController(v).navigate(R.id.navigation_roundup_settings);
        });

        binding.rowCreditSimulator.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), CibilSimulatorActivity.class);
            startActivity(intent);
        });

        binding.rowBillSplitter.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), BillSplitterActivity.class);
            startActivity(intent);
        });

        binding.rowMoodInsight.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), MoodInsightActivity.class);
            startActivity(intent);
        });

        binding.rowLiteracyLessons.setOnClickListener(v -> {
            androidx.navigation.Navigation.findNavController(v).navigate(R.id.navigation_literacy_lessons);
        });

        binding.rowCarbonTracker.setOnClickListener(v -> {
            androidx.navigation.Navigation.findNavController(v).navigate(R.id.navigation_carbon_tracker);
        });

        binding.rowExport.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), ExportActivity.class);
            startActivity(intent);
        });

        binding.btnLogout.setOnClickListener(v -> logoutUser());
    }

    private void loadProfileInfo() {
        if (mAuth.getCurrentUser() == null || getContext() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        binding.txtUserEmail.setText(mAuth.getCurrentUser().getEmail());

        mFirestore.collection("users").document(uid).addSnapshotListener((documentSnapshot, error) -> {
            if (error != null || getContext() == null) return;

            if (documentSnapshot != null && documentSnapshot.exists()) {
                String name = documentSnapshot.getString("name");
                String photoUrl = documentSnapshot.getString("photoUrl");
                Boolean bioEnabled = documentSnapshot.getBoolean("biometricEnabled");
                Boolean notifEnabled = documentSnapshot.getBoolean("notificationsEnabled");
                String currencyCode = documentSnapshot.getString("currency");
                String currencySymbol = documentSnapshot.getString("currencySymbol");
                String themeMode = documentSnapshot.getString("themeMode");

                if (binding != null) {
                    if (name != null && !name.trim().isEmpty()) {
                        binding.txtUserName.setText(name);
                        binding.txtAvatarLetter.setText(name.substring(0, 1).toUpperCase(Locale.US));
                    } else {
                        binding.txtUserName.setText("User");
                        binding.txtAvatarLetter.setText("U");
                    }

                    // Load Profile Photo using Glide if available
                    if (photoUrl != null && !photoUrl.trim().isEmpty()) {
                        binding.txtAvatarLetter.setVisibility(View.GONE);
                        binding.imgAvatar.setVisibility(View.VISIBLE);
                        Glide.with(this)
                                .load(photoUrl)
                                .placeholder(R.drawable.ic_placeholder)
                                .into(binding.imgAvatar);
                    } else {
                        binding.txtAvatarLetter.setVisibility(View.VISIBLE);
                        binding.imgAvatar.setVisibility(View.GONE);
                    }

                    if (bioEnabled != null) {
                        binding.switchBiometrics.setOnCheckedChangeListener(null);
                        binding.switchBiometrics.setChecked(bioEnabled);
                        binding.switchBiometrics.setOnCheckedChangeListener((buttonView, isChecked) -> updateBiometricSetting(isChecked));
                    }

                    if (notifEnabled != null) {
                        binding.switchNotifications.setOnCheckedChangeListener(null);
                        binding.switchNotifications.setChecked(notifEnabled);
                        binding.switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> updateNotificationsSetting(isChecked));
                    }

                    if (currencyCode != null && currencySymbol != null) {
                        binding.txtSelectedCurrency.setText(String.format("%s (%s)", currencyCode, currencySymbol));
                    } else {
                        binding.txtSelectedCurrency.setText("INR (₹)");
                    }

                    if (themeMode != null) {
                        binding.txtSelectedTheme.setText(themeMode);
                    } else {
                        binding.txtSelectedTheme.setText("System");
                    }
                }
            }
        });
    }

    private void loadTimeoutInfo() {
        if (getContext() != null && binding != null) {
            int seconds = SharedPreferencesManager.getInstance(getContext()).getLockTimeoutSeconds();
            binding.txtLockTimeout.setText(seconds + " Seconds");
        }
    }

    private void selectProfilePhoto() {
        pickImageLauncher.launch("image/*");
    }

    private void uploadProfileImage(Uri uri) {
        if (mAuth.getCurrentUser() == null || getContext() == null) return;
        BaseActivity activity = (BaseActivity) getActivity();
        if (activity != null) activity.showLoadingDialog();

        String uid = mAuth.getCurrentUser().getUid();
        StorageReference ref = FirebaseStorage.getInstance().getReference().child("users/" + uid + "/profile.jpg");

        ref.putFile(uri)
                .addOnSuccessListener(taskSnapshot -> ref.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                    String downloadUrl = downloadUri.toString();
                    mFirestore.collection("users").document(uid)
                            .update("photoUrl", downloadUrl)
                            .addOnSuccessListener(aVoid -> {
                                if (activity != null) {
                                    activity.hideLoadingDialog();
                                    activity.showSnackbar("Profile photo uploaded!", "SUCCESS");
                                }
                            })
                            .addOnFailureListener(e -> {
                                if (activity != null) {
                                    activity.hideLoadingDialog();
                                    activity.showSnackbar("Failed to save URL: " + e.getMessage(), "ERROR");
                                }
                            });
                }))
                .addOnFailureListener(e -> {
                    if (activity != null) {
                        activity.hideLoadingDialog();
                        activity.showSnackbar("Upload failed: " + e.getMessage(), "ERROR");
                    }
                });
    }

    private void showEditNameDialog() {
        BaseActivity activity = (BaseActivity) getActivity();
        if (activity == null) return;

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
        if (mAuth.getCurrentUser() == null || getContext() == null) return;
        BaseActivity activity = (BaseActivity) getActivity();

        mFirestore.collection("users").document(mAuth.getCurrentUser().getUid())
                .update("name", newName)
                .addOnSuccessListener(aVoid -> {
                    if (activity != null) activity.showSnackbar("Profile name updated!", "SUCCESS");
                })
                .addOnFailureListener(e -> {
                    if (activity != null) activity.showSnackbar("Update failed: " + e.getMessage(), "ERROR");
                });
    }

    private void showChangePasswordDialog() {
        BaseActivity activity = (BaseActivity) getActivity();
        if (activity == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Change Password");

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        EditText oldPasswordInput = new EditText(getContext());
        oldPasswordInput.setHint("Current Password");
        oldPasswordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(oldPasswordInput);

        EditText newPasswordInput = new EditText(getContext());
        newPasswordInput.setHint("New Password (min 6 chars)");
        newPasswordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(newPasswordInput);

        builder.setView(layout);

        builder.setPositiveButton("Update", (dialog, which) -> {
            String oldPass = oldPasswordInput.getText().toString().trim();
            String newPass = newPasswordInput.getText().toString().trim();

            if (oldPass.isEmpty() || newPass.length() < 6) {
                activity.showSnackbar("Please enter valid credentials", "ERROR");
                return;
            }

            activity.showLoadingDialog();
            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null && user.getEmail() != null) {
                AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), oldPass);
                user.reauthenticate(credential)
                        .addOnSuccessListener(aVoid -> user.updatePassword(newPass)
                                .addOnSuccessListener(unused -> {
                                    activity.hideLoadingDialog();
                                    activity.showSnackbar("Password updated successfully!", "SUCCESS");
                                })
                                .addOnFailureListener(e -> {
                                    activity.hideLoadingDialog();
                                    activity.showSnackbar("Password update failed: " + e.getMessage(), "ERROR");
                                }))
                        .addOnFailureListener(e -> {
                            activity.hideLoadingDialog();
                            activity.showSnackbar("Reauthentication failed: " + e.getMessage(), "ERROR");
                        });
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void selectBaseCurrency() {
        if (mAuth.getCurrentUser() == null || getContext() == null) return;
        BaseActivity activity = (BaseActivity) getActivity();

        String uid = mAuth.getCurrentUser().getUid();
        String currentCode = CurrencyManager.getInstance().getCurrentCurrencyCode();

        CurrencyPickerBottomSheet.newInstance(currentCode, currency -> {
            CurrencyManager.getInstance().saveToFirestore(uid, currency.code, currency.symbol, () -> {
                if (binding != null) {
                    binding.txtSelectedCurrency.setText(String.format("%s (%s)", currency.code, currency.symbol));
                    if (activity != null) activity.showSnackbar("Base currency updated!", "SUCCESS");
                }
            });
        }).show(getParentFragmentManager(), "CURRENCY_PICKER");
    }

    private void showThemeSelectionDialog() {
        String[] themes = {"System", "Light", "Dark"};
        int checkedItem = 0;
        String currentTheme = binding.txtSelectedTheme.getText().toString();
        for (int i = 0; i < themes.length; i++) {
            if (themes[i].equalsIgnoreCase(currentTheme)) {
                checkedItem = i;
                break;
            }
        }

        new AlertDialog.Builder(getContext())
                .setTitle("Select App Theme")
                .setSingleChoiceItems(themes, checkedItem, (dialog, which) -> {
                    String selected = themes[which];
                    updateTheme(selected);
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateTheme(String mode) {
        if (mAuth.getCurrentUser() == null || getContext() == null) return;
        BaseActivity activity = (BaseActivity) getActivity();
        String uid = mAuth.getCurrentUser().getUid();

        mFirestore.collection("users").document(uid)
                .update("themeMode", mode)
                .addOnSuccessListener(aVoid -> {
                    if (binding != null) {
                        binding.txtSelectedTheme.setText(mode);
                    }
                    if ("Dark".equalsIgnoreCase(mode)) {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                    } else if ("Light".equalsIgnoreCase(mode)) {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                    } else {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                    }
                    if (activity != null) activity.showSnackbar("Theme settings updated!", "SUCCESS");
                });
    }

    private void showLockTimeoutDialog() {
        String[] options = {"15 Seconds", "30 Seconds", "60 Seconds", "2 Minutes", "5 Minutes"};
        int[] values = {15, 30, 60, 120, 300};
        
        int currentTimeout = SharedPreferencesManager.getInstance(getContext()).getLockTimeoutSeconds();
        int checkedItem = 2; // Default 60s
        for (int i = 0; i < values.length; i++) {
            if (values[i] == currentTimeout) {
                checkedItem = i;
                break;
            }
        }

        new AlertDialog.Builder(getContext())
                .setTitle("Set Auto Lock Timeout")
                .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                    int seconds = values[which];
                    SharedPreferencesManager.getInstance(getContext()).setLockTimeoutSeconds(seconds);
                    loadTimeoutInfo();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateBiometricSetting(boolean enabled) {
        if (mAuth.getCurrentUser() == null || getContext() == null) return;
        BaseActivity activity = (BaseActivity) getActivity();
        String uid = mAuth.getCurrentUser().getUid();

        mFirestore.collection("users").document(uid)
                .update("biometricEnabled", enabled)
                .addOnSuccessListener(aVoid -> {
                    SharedPreferencesManager.getInstance(getContext()).setBiometricEnabled(enabled);
                    if (activity != null) {
                        String msg = enabled ? "Biometric login enabled" : "Biometric login disabled";
                        activity.showSnackbar(msg, "SUCCESS");
                    }
                })
                .addOnFailureListener(e -> {
                    if (binding != null) {
                        binding.switchBiometrics.setOnCheckedChangeListener(null);
                        binding.switchBiometrics.setChecked(!enabled);
                        binding.switchBiometrics.setOnCheckedChangeListener((buttonView, isChecked) -> updateBiometricSetting(isChecked));
                    }
                    if (activity != null) {
                        activity.showSnackbar("Failed to update biometric settings: " + e.getMessage(), "ERROR");
                    }
                });
    }

    private void updateNotificationsSetting(boolean enabled) {
        if (mAuth.getCurrentUser() == null || getContext() == null) return;
        BaseActivity activity = (BaseActivity) getActivity();
        String uid = mAuth.getCurrentUser().getUid();

        mFirestore.collection("users").document(uid)
                .update("notificationsEnabled", enabled)
                .addOnSuccessListener(aVoid -> {
                    if (activity != null) {
                        String msg = enabled ? "Budget notification alerts enabled" : "Budget notification alerts disabled";
                        activity.showSnackbar(msg, "SUCCESS");
                    }
                })
                .addOnFailureListener(e -> {
                    if (binding != null) {
                        binding.switchNotifications.setOnCheckedChangeListener(null);
                        binding.switchNotifications.setChecked(!enabled);
                        binding.switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> updateNotificationsSetting(isChecked));
                    }
                    if (activity != null) {
                        activity.showSnackbar("Failed to update notification settings: " + e.getMessage(), "ERROR");
                    }
                });
    }

    private void confirmClearData() {
        new AlertDialog.Builder(getContext())
                .setTitle("Clear All Data")
                .setMessage("Delete all your transactions, budgets, ledger history, and goals? This cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> confirmClearDataDoubleCheck())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmClearDataDoubleCheck() {
        new AlertDialog.Builder(getContext())
                .setTitle("Are you absolutely sure?")
                .setMessage("This will completely wipe out your account history and start fresh. Proceed?")
                .setPositiveButton("Yes, Clear Everything", (dialog, which) -> clearAllUserData())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void clearAllUserData() {
        if (mAuth.getCurrentUser() == null || getContext() == null) return;
        BaseActivity activity = (BaseActivity) getActivity();
        if (activity != null) activity.showLoadingDialog();

        String uid = mAuth.getCurrentUser().getUid();
        String[] subcollections = {"transactions", "budgets", "savings_goals", "categories", "notifications", "customers", "customer_txns"};

        // Perform batch deletes for each subcollection
        for (String coll : subcollections) {
            mFirestore.collection("users").document(uid).collection(coll).get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        if (queryDocumentSnapshots.isEmpty()) return;
                        WriteBatch batch = mFirestore.batch();
                        for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                            batch.delete(doc.getReference());
                        }
                        batch.commit();
                    });
        }

        // Delete profile photo in storage if it exists
        FirebaseStorage.getInstance().getReference().child("users/" + uid + "/profile.jpg").delete()
                .addOnCompleteListener(task -> {
                    // Sign out and redirect
                    SharedPreferencesManager.getInstance(getContext()).clearAll();
                    mFirestore.collection("users").document(uid).delete().addOnCompleteListener(deleteTask -> {
                        if (activity != null) activity.hideLoadingDialog();
                        logoutUser();
                    });
                });
    }

    private void logoutUser() {
        mAuth.signOut();
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build();
        GoogleSignInClient client = GoogleSignIn.getClient(requireActivity(), gso);
        client.signOut().addOnCompleteListener(task -> {
            Intent intent = new Intent(getContext(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
