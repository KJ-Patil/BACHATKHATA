package com.example.bachatkhata;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bachatkhata.databinding.ItemTransactionBinding;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class TransactionAdapter extends ListAdapter<Transaction, TransactionAdapter.TransactionViewHolder> {

    private final OnItemClickListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.US);

    public TransactionAdapter(OnItemClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<Transaction> DIFF_CALLBACK = new DiffUtil.ItemCallback<Transaction>() {
        @Override
        public boolean areItemsTheSame(@NonNull Transaction oldItem, @NonNull Transaction newItem) {
            return java.util.Objects.equals(oldItem.getId(), newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Transaction oldItem, @NonNull Transaction newItem) {
            return oldItem.getAmount() == newItem.getAmount() &&
                    java.util.Objects.equals(oldItem.getType(), newItem.getType()) &&
                    java.util.Objects.equals(oldItem.getCategory(), newItem.getCategory()) &&
                    java.util.Objects.equals(oldItem.getNote(), newItem.getNote()) &&
                    (oldItem.getDate() == null ? newItem.getDate() == null :
                     newItem.getDate() != null && oldItem.getDate().getTime() == newItem.getDate().getTime());
        }
    };

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTransactionBinding binding = ItemTransactionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new TransactionViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    public interface OnItemClickListener {
        void onItemClick(Transaction transaction);
    }

    class TransactionViewHolder extends RecyclerView.ViewHolder {
        private final ItemTransactionBinding binding;

        public TransactionViewHolder(ItemTransactionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onItemClick(getItem(pos));
                }
            });
        }

        public void bind(Transaction transaction) {
            String noteText = transaction.getNote();
            if (noteText == null || noteText.trim().isEmpty()) {
                binding.txtTransactionNote.setText(transaction.getCategory());
            } else {
                binding.txtTransactionNote.setText(noteText);
            }

            String dateStr = transaction.getDate() != null ? dateFormat.format(transaction.getDate()) : "";
            binding.txtCategoryAndDate.setText(String.format("%s • %s", transaction.getCategory(), dateStr));

            String formattedAmount = CurrencyManager.getInstance().formatAmount(transaction.getAmount());
            if ("income".equalsIgnoreCase(transaction.getType())) {
                binding.txtAmount.setText(String.format("+%s", formattedAmount));
                binding.txtAmount.setTextColor(Color.parseColor("#3DAF85")); // colorSecondary dark/green
            } else {
                binding.txtAmount.setText(String.format("-%s", formattedAmount));
                binding.txtAmount.setTextColor(Color.parseColor("#E24B4A")); // colorDanger
            }

            // Set emoji representation (fallback to default emoji if missing)
            binding.txtCategoryEmoji.setText(getCategoryEmoji(transaction.getCategory()));

            // Light primary color background for icon box
            binding.layoutIconFrame.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#1A7C6FE0")));
        }

        private String getCategoryEmoji(String category) {
            if (category == null) return "💰";
            switch (category.toLowerCase()) {
                case "food": return "🍛";
                case "transport": return "🚌";
                case "shopping": return "🛍️";
                case "bills": return "💡";
                case "health": return "🏥";
                case "entertainment": return "🎮";
                case "education": return "📚";
                case "rent": return "🏠";
                case "personal care": return "💆";
                case "travel": return "✈️";
                case "gifts": return "🎁";
                case "salary": return "💰";
                case "freelance": return "💻";
                default: return "📦";
            }
        }
    }
}
