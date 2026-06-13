package com.example.bachatkhata;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bachatkhata.databinding.LayoutSortBottomSheetBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class SortBottomSheet extends BottomSheetDialogFragment {

    public interface OnSortAppliedListener {
        void onSortApplied(String option); // "NEWEST", "OLDEST", "HIGHEST", "LOWEST"
    }

    private LayoutSortBottomSheetBinding binding;
    private OnSortAppliedListener listener;
    private String selectedOption = "NEWEST";

    public static SortBottomSheet newInstance(String selectedOption, OnSortAppliedListener listener) {
        SortBottomSheet sheet = new SortBottomSheet();
        sheet.selectedOption = selectedOption;
        sheet.listener = listener;
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = LayoutSortBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Pre-select current sort option
        if ("OLDEST".equalsIgnoreCase(selectedOption)) {
            binding.rbOldest.setChecked(true);
        } else if ("HIGHEST".equalsIgnoreCase(selectedOption)) {
            binding.rbHighest.setChecked(true);
        } else if ("LOWEST".equalsIgnoreCase(selectedOption)) {
            binding.rbLowest.setChecked(true);
        } else {
            binding.rbNewest.setChecked(true);
        }

        binding.btnApplySort.setOnClickListener(v -> {
            String option = "NEWEST";
            int checkedId = binding.rgSort.getCheckedRadioButtonId();
            if (checkedId == R.id.rbOldest) {
                option = "OLDEST";
            } else if (checkedId == R.id.rbHighest) {
                option = "HIGHEST";
            } else if (checkedId == R.id.rbLowest) {
                option = "LOWEST";
            }

            if (listener != null) {
                listener.onSortApplied(option);
            }
            dismiss();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
