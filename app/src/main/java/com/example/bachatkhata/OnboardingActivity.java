package com.example.bachatkhata;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.example.bachatkhata.databinding.ActivityOnboardingBinding;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class OnboardingActivity extends AppCompatActivity {

    private ActivityOnboardingBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();

        // Configure ViewPager2 Adapter
        binding.viewPagerOnboarding.setAdapter(new OnboardingAdapter(this));

        // Connect TabLayout with ViewPager2
        new TabLayoutMediator(binding.tabLayoutIndicator, binding.viewPagerOnboarding,
                (tab, position) -> {
                    // Dot indicators are handled by the selector in layout XML
                }).attach();

        // Listen for Page Changes to alter Next button text
        binding.viewPagerOnboarding.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                if (position == 2) {
                    binding.btnNext.setText(getString(R.string.get_started));
                } else {
                    binding.btnNext.setText(getString(R.string.next));
                }
            }
        });

        // Click Actions
        binding.btnNext.setOnClickListener(v -> {
            int current = binding.viewPagerOnboarding.getCurrentItem();
            if (current < 2) {
                binding.viewPagerOnboarding.setCurrentItem(current + 1, true);
            } else {
                completeOnboarding();
            }
        });

        binding.btnSkip.setOnClickListener(v -> completeOnboarding());
    }

    private void completeOnboarding() {
        if (mAuth.getCurrentUser() == null) {
            // Unauthenticated user fallback
            startActivity(new Intent(OnboardingActivity.this, LoginActivity.class));
            finish();
            return;
        }

        showLoading(true);
        String uid = mAuth.getCurrentUser().getUid();

        mFirestore.collection("users").document(uid)
                .update("onboardingComplete", true)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    Intent intent = new Intent(OnboardingActivity.this, PinSetupActivity.class);
                    intent.putExtra("mode", "SETUP");
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    // Even if update fails, proceed so user isn't stuck
                    Intent intent = new Intent(OnboardingActivity.this, PinSetupActivity.class);
                    intent.putExtra("mode", "SETUP");
                    startActivity(intent);
                    finish();
                });
    }

    private void showLoading(boolean isLoading) {
        binding.loaderOverlay.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }

    private class OnboardingAdapter extends FragmentStateAdapter {

        public OnboardingAdapter(@NonNull AppCompatActivity activity) {
            super(activity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return OnboardingPageFragment.newInstance(
                            R.drawable.ic_wallet,
                            getString(R.string.onboarding_title_1),
                            getString(R.string.onboarding_subtitle_1)
                    );
                case 1:
                    return OnboardingPageFragment.newInstance(
                            R.drawable.ic_piggy_bank,
                            getString(R.string.onboarding_title_2),
                            getString(R.string.onboarding_subtitle_2)
                    );
                case 2:
                default:
                    return OnboardingPageFragment.newInstance(
                            R.drawable.ic_budget,
                            getString(R.string.onboarding_title_3),
                            getString(R.string.onboarding_subtitle_3)
                    );
            }
        }

        @Override
        public int getItemCount() {
            return 3;
        }
    }
}
