package com.example.bachatkhata;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bachatkhata.databinding.LayoutAccountPickerBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class AccountPickerBottomSheet extends BottomSheetDialogFragment {

    public interface OnAccountSelectedListener {
        void onAccountSelected(String accountName);
    }

    private LayoutAccountPickerBinding binding;
    private OnAccountSelectedListener listener;

    public static AccountPickerBottomSheet newInstance(OnAccountSelectedListener listener) {
        AccountPickerBottomSheet sheet = new AccountPickerBottomSheet();
        sheet.listener = listener;
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = LayoutAccountPickerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.cardCash.setOnClickListener(v -> selectAccount("Cash"));
        binding.cardUPI.setOnClickListener(v -> selectAccount("UPI"));
        binding.cardBankCard.setOnClickListener(v -> selectAccount("Bank Card"));
        binding.cardNetBanking.setOnClickListener(v -> selectAccount("Net Banking"));
    }

    private void selectAccount(String accountName) {
        if (listener != null) {
            listener.onAccountSelected(accountName);
        }
        dismiss();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
