package com.example.bachatkhata;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import com.example.bachatkhata.databinding.ActivityAboutBinding;

/**
 * App version, credits and policy links.
 *
 * <p>Policy buttons follow the same rule as the Help screen's contact cards: they
 * appear only when a URL is configured, so the app never ships a link that 404s.
 */
public class AboutActivity extends BaseActivity {

    private ActivityAboutBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAboutBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());

        binding.txtVersion.setText(getString(R.string.about_version, readVersionName()));
        setupPolicyLinks();
    }

    /** Version straight from the installed package, so it can never drift from the build. */
    private String readVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            // The app is always able to see its own package; unreachable in practice.
            return "—";
        }
    }

    private void setupPolicyLinks() {
        String privacy = BuildConfig.PRIVACY_POLICY_URL;
        String terms = BuildConfig.TERMS_URL;

        boolean hasPrivacy = privacy != null && !privacy.trim().isEmpty();
        boolean hasTerms = terms != null && !terms.trim().isEmpty();

        if (hasPrivacy) {
            binding.btnPrivacyPolicy.setVisibility(View.VISIBLE);
            binding.btnPrivacyPolicy.setOnClickListener(v -> openUrl(privacy));
        }
        if (hasTerms) {
            binding.btnTerms.setVisibility(View.VISIBLE);
            binding.btnTerms.setOnClickListener(v -> openUrl(terms));
        }

        binding.txtPolicyUnavailable.setVisibility(
                hasPrivacy || hasTerms ? View.GONE : View.VISIBLE);
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url.trim())));
        } catch (ActivityNotFoundException e) {
            showSnackbar(getString(R.string.help_no_app_for_action), "ERROR");
        }
    }
}
