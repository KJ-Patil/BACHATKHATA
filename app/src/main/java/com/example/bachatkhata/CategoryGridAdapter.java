package com.example.bachatkhata;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bachatkhata.databinding.ItemCategoryGridCellBinding;

import java.util.ArrayList;
import java.util.List;

public class CategoryGridAdapter extends RecyclerView.Adapter<CategoryGridAdapter.CategoryViewHolder> {

    private final List<Category> categories = new ArrayList<>();
    private int selectedPosition = -1;
    private final OnCategoryClickListener listener;

    public CategoryGridAdapter(OnCategoryClickListener listener) {
        this.listener = listener;
    }

    public void setCategories(List<Category> list) {
        this.categories.clear();
        this.categories.addAll(list);
        this.selectedPosition = -1; // Reset selection
        notifyDataSetChanged();
    }

    public Category getSelectedCategory() {
        if (selectedPosition >= 0 && selectedPosition < categories.size()) {
            return categories.get(selectedPosition);
        }
        return null;
    }

    public void setSelectedCategoryByName(String name) {
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).getName().equalsIgnoreCase(name)) {
                selectedPosition = i;
                notifyDataSetChanged();
                break;
            }
        }
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCategoryGridCellBinding binding = ItemCategoryGridCellBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new CategoryViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        holder.bind(categories.get(position), position == selectedPosition);
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    public interface OnCategoryClickListener {
        void onCategoryClick(Category category);
    }

    class CategoryViewHolder extends RecyclerView.ViewHolder {
        private final ItemCategoryGridCellBinding binding;

        public CategoryViewHolder(ItemCategoryGridCellBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    notifyItemChanged(selectedPosition);
                    selectedPosition = pos;
                    notifyItemChanged(selectedPosition);
                    if (listener != null) {
                        listener.onCategoryClick(categories.get(pos));
                    }
                }
            });
        }

        public void bind(Category category, boolean isSelected) {
            binding.txtCategoryName.setText(category.getName());
            binding.txtCategoryEmoji.setText(category.getIcon());

            String hexColor = category.getColor() != null ? category.getColor() : "#7C6FE0";
            int baseColor;
            try {
                baseColor = Color.parseColor(hexColor);
            } catch (Exception e) {
                baseColor = Color.parseColor("#7C6FE0");
            }

            if (isSelected) {
                // Selected State: Solid category color bg, white text, and stroke = 2dp category color
                binding.layoutIconCircle.setBackgroundColor(baseColor);
                binding.txtCategoryEmoji.setTextColor(Color.WHITE);
                binding.cardCategoryIcon.setStrokeColor(baseColor);
                binding.cardCategoryIcon.setStrokeWidth(6); // 2dp in thickness (approx 6px)
                binding.txtCategoryName.setTextColor(Color.parseColor("#7C6FE0"));
            } else {
                // Unselected State: 20% alpha category color bg
                int alphaColor = Color.argb(51, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)); // 51 is 20% of 255
                binding.layoutIconCircle.setBackgroundColor(alphaColor);
                binding.txtCategoryEmoji.setTextColor(baseColor);
                binding.cardCategoryIcon.setStrokeColor(Color.parseColor("#E0DEFF")); // default card border
                binding.cardCategoryIcon.setStrokeWidth(3);
                binding.txtCategoryName.setTextColor(Color.parseColor("#6B6B8A"));
            }
        }
    }
}
