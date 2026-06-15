package com.example.bachatkhata;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.bachatkhata.databinding.FragmentOnboardingPageBinding;

public class OnboardingPageFragment extends Fragment {

    private static final String ARG_LOTTIE_RAW_RES = "lottie_raw_res";
    private static final String ARG_TITLE = "title";
    private static final String ARG_SUBTITLE = "subtitle";

    private FragmentOnboardingPageBinding binding;

    public OnboardingPageFragment() {
        // Required empty public constructor
    }

    public static OnboardingPageFragment newInstance(int lottieRawRes, String title, String subtitle) {
        OnboardingPageFragment fragment = new OnboardingPageFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_LOTTIE_RAW_RES, lottieRawRes);
        args.putString(ARG_TITLE, title);
        args.putString(ARG_SUBTITLE, subtitle);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentOnboardingPageBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getArguments() != null) {
            int lottieRawRes = getArguments().getInt(ARG_LOTTIE_RAW_RES);
            String title = getArguments().getString(ARG_TITLE);
            String subtitle = getArguments().getString(ARG_SUBTITLE);

            binding.lottieOnboarding.setAnimation(lottieRawRes);
            binding.txtOnboardingTitle.setText(title);
            binding.txtOnboardingSubtitle.setText(subtitle);

            // Animate onboarding entry fluidly
            binding.lottieOnboarding.setAlpha(0f);
            binding.lottieOnboarding.setTranslationY(40f);
            binding.txtOnboardingTitle.setAlpha(0f);
            binding.txtOnboardingTitle.setTranslationY(20f);
            binding.txtOnboardingSubtitle.setAlpha(0f);
            binding.txtOnboardingSubtitle.setTranslationY(20f);

            binding.lottieOnboarding.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(400)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();

            binding.txtOnboardingTitle.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(300)
                    .setStartDelay(150)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();

            binding.txtOnboardingSubtitle.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(300)
                    .setStartDelay(250)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
