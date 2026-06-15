package com.example.bachatkhata;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.example.bachatkhata.databinding.LayoutAddDonationBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AddDonationBottomSheet extends BottomSheetDialogFragment {

    private LayoutAddDonationBinding binding;
    private final List<String> projects = new ArrayList<>();
    private Date selectedDate = new Date();
    private OnDonationAddedListener listener;

    public interface OnDonationAddedListener {
        void onDonationAdded(String name, String phone, double amount, String project, Date date);
    }

    public static AddDonationBottomSheet newInstance(ArrayList<String> projectList) {
        AddDonationBottomSheet fragment = new AddDonationBottomSheet();
        Bundle args = new Bundle();
        args.putStringArrayList("projects", projectList);
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnDonationAddedListener(OnDonationAddedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = LayoutAddDonationBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            List<String> list = getArguments().getStringArrayList("projects");
            if (list != null) {
                projects.addAll(list);
            }
        }

        if (projects.isEmpty()) {
            projects.add("General Campaign");
        }

        setupUI();
    }

    private void setupUI() {
        // Setup Spinner
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, projects);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerDonationProjects.setAdapter(adapter);

        // Date selector
        binding.cardDonationDate.setOnClickListener(v -> showDatePicker());

        // Save button
        binding.btnSaveDonation.setOnClickListener(v -> {
            String name = binding.inputDonorName.getText().toString().trim();
            String phone = binding.inputDonorPhone.getText().toString().trim();
            String amountStr = binding.inputDonationAmount.getText().toString().trim();
            String project = binding.spinnerDonationProjects.getSelectedItem() != null 
                    ? binding.spinnerDonationProjects.getSelectedItem().toString() 
                    : "General Campaign";

            if (name.isEmpty() || amountStr.isEmpty()) {
                Toast.makeText(getContext(), "Donor Name and Amount are required", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                double amount = Double.parseDouble(amountStr);
                if (amount <= 0) {
                    Toast.makeText(getContext(), "Invalid donation amount", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (listener != null) {
                    listener.onDonationAdded(name, phone, amount, project, selectedDate);
                }
                dismiss();
            } catch (NumberFormatException e) {
                Toast.makeText(getContext(), "Invalid number format", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showDatePicker() {
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Donation Date")
                .setSelection(selectedDate.getTime())
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            selectedDate = new Date(selection);
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.US);
            binding.txtDonationDate.setText("Date: " + sdf.format(selectedDate));
        });

        datePicker.show(getParentFragmentManager(), "date_picker");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
