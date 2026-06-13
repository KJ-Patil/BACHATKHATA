package com.example.bachatkhata;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bachatkhata.databinding.LayoutSetBudgetBinding;
import com.example.bachatkhata.databinding.ItemCategoryChipBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SetBudgetBottomSheet extends BottomSheetDialogFragment {

    public interface OnBudgetSavedListener {
        void onBudgetSaved();
    }

    private LayoutSetBudgetBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;

    private int targetMonth;
    private int targetYear;
    private Budget editBudget = null; // non-null if edit mode
    private OnBudgetSavedListener listener;

    private CategoryChipAdapter chipAdapter;
    private final List<Category> expenseCategories = new ArrayList<>();
    private Category selectedCategory = null;

    public static SetBudgetBottomSheet newInstance(int month, int year, @Nullable Budget budget, OnBudgetSavedListener listener) {
        SetBudgetBottomSheet sheet = new SetBudgetBottomSheet();
        sheet.targetMonth = month;
        sheet.targetYear = year;
        sheet.editBudget = budget;
        sheet.listener = listener;
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = LayoutSetBudgetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();

        setupRecyclerView();

        if (editBudget != null) {
            binding.txtSheetTitle.setText("Edit Budget");
            binding.etLimitAmount.setText(String.valueOf(editBudget.getLimitAmount()));
        } else {
            binding.txtSheetTitle.setText("Set Budget");
        }

        binding.btnSaveBudget.setOnClickListener(v -> saveBudget());

        loadExpenseCategories();
    }

    private void setupRecyclerView() {
        chipAdapter = new CategoryChipAdapter();
        binding.rvCategoryChips.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        binding.rvCategoryChips.setAdapter(chipAdapter);
    }

    private void loadExpenseCategories() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        mFirestore.collection("users").document(uid).collection("categories")
                .whereEqualTo("type", "expense")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    expenseCategories.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        expenseCategories.add(Category.fromDocument(doc));
                    }
                    chipAdapter.notifyDataSetChanged();

                    // Pre-select category chip if in edit mode
                    if (editBudget != null) {
                        chipAdapter.selectCategoryByName(editBudget.getCategory());
                    }
                });
    }

    private void saveBudget() {
        String amountStr = binding.etLimitAmount.getText().toString().trim();
        if (amountStr.isEmpty()) {
            Snackbar.make(binding.getRoot(), "Please enter limit amount.", Snackbar.LENGTH_SHORT).show();
            return;
        }

        double limit;
        try {
            limit = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            Snackbar.make(binding.getRoot(), "Invalid amount.", Snackbar.LENGTH_SHORT).show();
            return;
        }

        if (limit <= 0) {
            Snackbar.make(binding.getRoot(), "Limit must be greater than zero.", Snackbar.LENGTH_SHORT).show();
            return;
        }

        if (selectedCategory == null && editBudget == null) {
            Snackbar.make(binding.getRoot(), "Please select a category.", Snackbar.LENGTH_SHORT).show();
            return;
        }

        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        String categoryName = editBudget != null ? editBudget.getCategory() : selectedCategory.getName();
        // Self-merging composite ID: category_month_year
        String budgetId = categoryName + "_" + targetMonth + "_" + targetYear;

        Map<String, Object> data = new HashMap<>();
        data.put("id", budgetId);
        data.put("category", categoryName);
        data.put("limitAmount", limit);
        data.put("period", "monthly");
        data.put("month", targetMonth);
        data.put("year", targetYear);

        mFirestore.collection("users").document(uid).collection("budgets").document(budgetId)
                .set(data)
                .addOnSuccessListener(aVoid -> {
                    if (listener != null) {
                        listener.onBudgetSaved();
                    }
                    dismiss();
                })
                .addOnFailureListener(e -> {
                    Snackbar.make(binding.getRoot(), "Failed to save: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
                });
    }

    private class CategoryChipAdapter extends RecyclerView.Adapter<CategoryChipAdapter.ViewHolder> {

        private int selectedPos = -1;

        public void selectCategoryByName(String name) {
            for (int i = 0; i < expenseCategories.size(); i++) {
                if (expenseCategories.get(i).getName().equalsIgnoreCase(name)) {
                    selectedPos = i;
                    selectedCategory = expenseCategories.get(i);
                    notifyDataSetChanged();
                    break;
                }
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemCategoryChipBinding chipBinding = ItemCategoryChipBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(chipBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(expenseCategories.get(position), position);
        }

        @Override
        public int getItemCount() {
            return expenseCategories.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final ItemCategoryChipBinding chipBinding;

            public ViewHolder(ItemCategoryChipBinding chipBinding) {
                super(chipBinding.getRoot());
                this.chipBinding = chipBinding;
            }

            public void bind(Category category, int pos) {
                Chip chip = chipBinding.chipCategory;
                chip.setText(category.getIcon() + " " + category.getName());
                chip.setChecked(pos == selectedPos);
                
                chip.setOnClickListener(v -> {
                    int prevSelected = selectedPos;
                    selectedPos = pos;
                    selectedCategory = category;
                    notifyItemChanged(prevSelected);
                    notifyItemChanged(selectedPos);
                });
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
