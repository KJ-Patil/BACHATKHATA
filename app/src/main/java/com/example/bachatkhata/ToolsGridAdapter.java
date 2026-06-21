package com.example.bachatkhata;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bachatkhata.databinding.ItemDataToolCellBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Grid adapter for the "Data &amp; Tools" screen. Each cell shows an emoji + label
 * and routes either to a navigation destination or an Activity, mirroring the
 * routing used by the Settings "Data &amp; Tools" list in {@link ProfileFragment}.
 */
public class ToolsGridAdapter extends RecyclerView.Adapter<ToolsGridAdapter.ToolViewHolder> {

    /** A single tool entry. Exactly one of {@code navDestId} / {@code activityClass} is set. */
    public static class ToolItem {
        final String emoji;
        final String label;
        final int navDestId;                  // 0 when routing via Activity
        final Class<?> activityClass;         // null when routing via NavController

        private ToolItem(String emoji, String label, int navDestId, Class<?> activityClass) {
            this.emoji = emoji;
            this.label = label;
            this.navDestId = navDestId;
            this.activityClass = activityClass;
        }

        /** Tool that opens a navigation destination. */
        static ToolItem nav(String emoji, String label, int navDestId) {
            return new ToolItem(emoji, label, navDestId, null);
        }

        /** Tool that launches an Activity. */
        static ToolItem activity(String emoji, String label, Class<?> activityClass) {
            return new ToolItem(emoji, label, 0, activityClass);
        }
    }

    public interface OnToolClickListener {
        void onToolClick(ToolItem tool);
    }

    private final List<ToolItem> tools = new ArrayList<>();
    private final OnToolClickListener listener;

    public ToolsGridAdapter(List<ToolItem> tools, OnToolClickListener listener) {
        this.tools.addAll(tools);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ToolViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDataToolCellBinding binding = ItemDataToolCellBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ToolViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ToolViewHolder holder, int position) {
        holder.bind(tools.get(position));
    }

    @Override
    public int getItemCount() {
        return tools.size();
    }

    class ToolViewHolder extends RecyclerView.ViewHolder {
        private final ItemDataToolCellBinding binding;

        ToolViewHolder(ItemDataToolCellBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onToolClick(tools.get(pos));
                }
            });
        }

        void bind(ToolItem tool) {
            binding.txtToolEmoji.setText(tool.emoji);
            binding.txtToolLabel.setText(tool.label);
        }
    }
}
