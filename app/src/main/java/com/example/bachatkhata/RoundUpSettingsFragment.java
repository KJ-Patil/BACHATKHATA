package com.example.bachatkhata;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.bachatkhata.databinding.FragmentRoundupSettingsBinding;

public class RoundUpSettingsFragment extends Fragment {

    private FragmentRoundupSettingsBinding binding;
    private SharedPreferencesManager prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentRoundupSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        prefs = SharedPreferencesManager.getInstance(requireContext());

        setupListeners();
        loadSettings();
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        binding.switchRoundUp.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.setRoundUpEnabled(isChecked);
            binding.cardLimitPicker.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        binding.toggleLimitGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                int limit = 10;
                if (checkedId == R.id.btnLimit50) {
                    limit = 50;
                } else if (checkedId == R.id.btnLimit100) {
                    limit = 100;
                }
                prefs.setRoundUpLimit(limit);
            }
        });
    }

    private void loadSettings() {
        boolean enabled = prefs.isRoundUpEnabled();
        binding.switchRoundUp.setChecked(enabled);
        binding.cardLimitPicker.setVisibility(enabled ? View.VISIBLE : View.GONE);

        int limit = prefs.getRoundUpLimit();
        if (limit == 50) {
            binding.toggleLimitGroup.check(R.id.btnLimit50);
        } else if (limit == 100) {
            binding.toggleLimitGroup.check(R.id.btnLimit100);
        } else {
            binding.toggleLimitGroup.check(R.id.btnLimit10);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
