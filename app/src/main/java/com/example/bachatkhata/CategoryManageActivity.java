package com.example.bachatkhata;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bachatkhata.databinding.ActivityCategoryManageBinding;
import com.example.bachatkhata.databinding.DialogAddCategoryBinding;
import com.example.bachatkhata.databinding.ItemCategoryManageBinding;
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoryManageActivity extends BaseActivity {

    private ActivityCategoryManageBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;
    private ListenerRegistration categoriesListener;

    private final List<Category> categoryList = new ArrayList<>();
    private CategoryAdapter adapter;
    private String selectedType = "expense"; // "expense" or "income"

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCategoryManageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();

        setupRecyclerView();
        setupToggleGroup();
        setupListeners();
        loadCategories();
    }

    private void setupRecyclerView() {
        adapter = new CategoryAdapter();
        binding.rvCategories.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCategories.setAdapter(adapter);
    }

    private void setupToggleGroup() {
        // Default color for Expense: Red
        binding.btnToggleExpense.setBackgroundColor(Color.parseColor("#E24B4A"));
        binding.btnToggleExpense.setTextColor(Color.WHITE);
        binding.btnToggleIncome.setBackgroundColor(Color.TRANSPARENT);
        binding.btnToggleIncome.setTextColor(Color.parseColor("#7C6FE0"));

        binding.toggleTypeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnToggleIncome) {
                // Selected Income: Green
                binding.btnToggleIncome.setBackgroundColor(Color.parseColor("#5DCAA5"));
                binding.btnToggleIncome.setTextColor(Color.WHITE);
                binding.btnToggleExpense.setBackgroundColor(Color.TRANSPARENT);
                binding.btnToggleExpense.setTextColor(Color.parseColor("#7C6FE0"));
                selectedType = "income";
            } else {
                // Selected Expense: Red
                binding.btnToggleExpense.setBackgroundColor(Color.parseColor("#E24B4A"));
                binding.btnToggleExpense.setTextColor(Color.WHITE);
                binding.btnToggleIncome.setBackgroundColor(Color.TRANSPARENT);
                binding.btnToggleIncome.setTextColor(Color.parseColor("#7C6FE0"));
                selectedType = "expense";
            }
            loadCategories();
        });
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnAddCategory.setOnClickListener(v -> showAddCategoryDialog());
    }

    private void loadCategories() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        showLoading(true);

        if (categoriesListener != null) {
            categoriesListener.remove();
        }

        categoriesListener = mFirestore.collection("users").document(uid)
                .collection("categories")
                .whereEqualTo("type", selectedType)
                .orderBy("name", Query.Direction.ASCENDING)
                .addSnapshotListener((value, error) -> {
                    showLoading(false);
                    if (error != null) {
                        return;
                    }

                    categoryList.clear();
                    if (value != null) {
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            categoryList.add(Category.fromDocument(doc));
                        }
                    }

                    if (categoryList.isEmpty()) {
                        binding.txtEmptyState.setVisibility(View.VISIBLE);
                        binding.rvCategories.setVisibility(View.GONE);
                    } else {
                        binding.txtEmptyState.setVisibility(View.GONE);
                        binding.rvCategories.setVisibility(View.VISIBLE);
                    }
                    adapter.notifyDataSetChanged();
                });
    }

    private void showAddCategoryDialog() {
        DialogAddCategoryBinding dialogBinding = DialogAddCategoryBinding.inflate(LayoutInflater.from(this));
        
        // Populate Emojis
        String[] emojis = {"🍔", "🛒", "🚕", "🎬", "🏥", "🏠", "👔", "🏫", "💰", "📈", "🎁", "🎮", "✈️", "💸", "🛠️", "🍕", "🍛", "💇", "💆", "🔌"};
        for (String emoji : emojis) {
            Chip chip = new Chip(this);
            chip.setText(emoji);
            chip.setCheckable(true);
            chip.setTextSize(18);
            chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#F4F3FF")));
            dialogBinding.chipGroupEmojis.addView(chip);
        }

        // Populate Colors
        String[] colors = {"#7C6FE0", "#5DCAA5", "#EF9F27", "#E24B4A", "#F0997B", "#85B7EB", "#AFA9EC", "#F4C0D1", "#ED93B1", "#3B82F6"};
        for (String colorHex : colors) {
            Chip chip = new Chip(this);
            chip.setText(" ");
            chip.setCheckable(true);
            chip.setChipIcon(getDrawable(R.drawable.ic_placeholder)); // simple dot/placeholder
            chip.setChipIconTint(ColorStateList.valueOf(Color.parseColor(colorHex)));
            chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor(colorHex)));
            dialogBinding.chipGroupColors.addView(chip);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Custom Category");
        builder.setView(dialogBinding.getRoot());
        
        builder.setPositiveButton("Add", (dialog, which) -> {
            String name = dialogBinding.etCategoryName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Category name cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            // Get selected emoji
            String selectedEmoji = "📦";
            int selectedEmojiId = dialogBinding.chipGroupEmojis.getCheckedChipId();
            if (selectedEmojiId != View.NO_ID) {
                Chip chip = dialogBinding.chipGroupEmojis.findViewById(selectedEmojiId);
                selectedEmoji = chip.getText().toString();
            }

            // Get selected color
            String selectedColor = "#7C6FE0";
            int selectedColorId = dialogBinding.chipGroupColors.getCheckedChipId();
            if (selectedColorId != View.NO_ID) {
                Chip chip = dialogBinding.chipGroupColors.findViewById(selectedColorId);
                // Get corresponding color index
                int index = dialogBinding.chipGroupColors.indexOfChild(chip);
                if (index >= 0 && index < colors.length) {
                    selectedColor = colors[index];
                }
            }

            addCategory(name, selectedEmoji, selectedColor);
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void addCategory(String name, String emoji, String color) {
        if (mAuth.getCurrentUser() == null) return;
        showLoading(true);
        String uid = mAuth.getCurrentUser().getUid();

        DocumentReference docRef = mFirestore.collection("users").document(uid)
                .collection("categories").document();

        Map<String, Object> category = new HashMap<>();
        category.put("id", docRef.getId());
        category.put("name", name);
        category.put("icon", emoji);
        category.put("color", color);
        category.put("type", selectedType);
        category.put("isDefault", false); // Custom category

        docRef.set(category)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    showSuccess("Category added successfully!");
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    showError("Failed to add category: " + e.getMessage());
                });
    }

    private void confirmDeleteCategory(Category category) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Category")
                .setMessage(String.format("Are you sure you want to delete '%s'? Any transaction under this category might not be properly classified.", category.getName()))
                .setPositiveButton("Delete", (dialog, which) -> deleteCategory(category))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteCategory(Category category) {
        if (mAuth.getCurrentUser() == null) return;
        showLoading(true);
        String uid = mAuth.getCurrentUser().getUid();

        mFirestore.collection("users").document(uid)
                .collection("categories").document(category.getId())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    showSuccess("Category deleted successfully!");
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    showError("Failed to delete category: " + e.getMessage());
                });
    }

    private void showLoading(boolean isLoading) {
        binding.loaderOverlay.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT)
                .setBackgroundTint(getResources().getColor(R.color.colorDanger))
                .show();
    }

    private void showSuccess(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT)
                .setBackgroundTint(getResources().getColor(R.color.colorSecondary))
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (categoriesListener != null) {
            categoriesListener.remove();
        }
    }

    private class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemCategoryManageBinding itemBinding = ItemCategoryManageBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(categoryList.get(position));
        }

        @Override
        public int getItemCount() {
            return categoryList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final ItemCategoryManageBinding itemBinding;

            public ViewHolder(ItemCategoryManageBinding itemBinding) {
                super(itemBinding.getRoot());
                this.itemBinding = itemBinding;
            }

            public void bind(Category category) {
                itemBinding.txtCategoryName.setText(category.getName());
                itemBinding.txtCategoryEmoji.setText(category.getIcon());

                int baseColor = Color.parseColor(category.getColor() != null ? category.getColor() : "#7C6FE0");
                itemBinding.layoutIconFrame.setBackgroundTintList(ColorStateList.valueOf(baseColor));

                if (category.getIsDefault()) {
                    itemBinding.txtCategoryType.setText("System Default");
                    itemBinding.btnDeleteCategory.setVisibility(View.GONE);
                } else {
                    itemBinding.txtCategoryType.setText("Custom Category");
                    itemBinding.btnDeleteCategory.setVisibility(View.VISIBLE);
                    itemBinding.btnDeleteCategory.setOnClickListener(v -> confirmDeleteCategory(category));
                }
            }
        }
    }
}
