package com.example.bachatkhata;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bachatkhata.databinding.FragmentTransactionsBinding;
import com.example.bachatkhata.databinding.ItemTransactionBinding;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TransactionsFragment extends Fragment {

    public static class AdapterItem {
        public static final int TYPE_HEADER = 0;
        public static final int TYPE_ITEM = 1;

        public int type;
        public String headerTitle;
        public Transaction transaction;

        public AdapterItem(String headerTitle) {
            this.type = TYPE_HEADER;
            this.headerTitle = headerTitle;
        }

        public AdapterItem(Transaction transaction) {
            this.type = TYPE_ITEM;
            this.transaction = transaction;
        }
    }

    private FragmentTransactionsBinding binding;
    private FirebaseAuth mAuth;
    private SectionedAdapter adapter;
    private final List<Transaction> allTransactions = new ArrayList<>();
    
    private String selectedTypeFilter = "ALL"; // "ALL", "INCOME", "EXPENSE"
    private String selectedSortOption = "NEWEST"; // "NEWEST", "OLDEST", "HIGHEST", "LOWEST"
    private String searchQuery = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentTransactionsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();

        setupRecyclerView();
        setupFilters();
        setupListeners();

        if (mAuth.getCurrentUser() != null) {
            loadTransactions(mAuth.getCurrentUser().getUid());
        }
    }

    private void setupRecyclerView() {
        adapter = new SectionedAdapter(transaction -> {
            Intent intent = new Intent(getContext(), TransactionDetailActivity.class);
            intent.putExtra("transaction", transaction);
            startActivity(intent);
        });
        binding.rvTransactions.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvTransactions.setAdapter(adapter);

        // Add Swipe Action callback using ItemTouchHelper
        ItemTouchHelper swipeHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public int getSwipeDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                if (viewHolder.getItemViewType() == AdapterItem.TYPE_HEADER) {
                    return 0;
                }
                return super.getSwipeDirs(recyclerView, viewHolder);
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                AdapterItem item = adapter.getItemAt(position);
                if (item != null && item.transaction != null) {
                    if (direction == ItemTouchHelper.LEFT) {
                        // Swipe Left: Delete
                        showDeleteConfirmationDialog(item.transaction, position);
                    } else if (direction == ItemTouchHelper.RIGHT) {
                        // Swipe Right: Edit
                        adapter.notifyItemChanged(position); // reset item visual swipe state
                        Intent intent = new Intent(getContext(), TransactionDetailActivity.class);
                        intent.putExtra("transaction", item.transaction);
                        intent.putExtra("isEditMode", true);
                        startActivity(intent);
                    }
                }
            }
        });
        swipeHelper.attachToRecyclerView(binding.rvTransactions);
    }

    private void setupFilters() {
        binding.chipGroupTypeFilters.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipFilterIncome) {
                selectedTypeFilter = "INCOME";
            } else if (id == R.id.chipFilterExpense) {
                selectedTypeFilter = "EXPENSE";
            } else {
                selectedTypeFilter = "ALL";
            }
            applyFiltersAndSort();
        });
    }

    private void setupListeners() {
        binding.btnSort.setOnClickListener(v -> {
            SortBottomSheet.newInstance(selectedSortOption, option -> {
                selectedSortOption = option;
                applyFiltersAndSort();
            }).show(getChildFragmentManager(), "SORT_SHEET");
        });

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString();
                applyFiltersAndSort();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.btnEmptyAdd.setOnClickListener(v -> 
            startActivity(new Intent(getContext(), AddTransactionActivity.class))
        );
    }

    private void loadTransactions(String uid) {
        TransactionRepository.getInstance().observeTransactions(uid, list -> {
            allTransactions.clear();
            allTransactions.addAll(list);
            applyFiltersAndSort();
        });
    }

    private void applyFiltersAndSort() {
        List<Transaction> filtered = new ArrayList<>();

        // 1. Filter by Type and Search Query
        for (Transaction t : allTransactions) {
            boolean typeMatches = "ALL".equals(selectedTypeFilter) ||
                    ("INCOME".equals(selectedTypeFilter) && "income".equalsIgnoreCase(t.getType())) ||
                    ("EXPENSE".equals(selectedTypeFilter) && "expense".equalsIgnoreCase(t.getType()));

            boolean searchMatches = searchQuery.trim().isEmpty() ||
                    (t.getCategory() != null && t.getCategory().toLowerCase().contains(searchQuery.toLowerCase())) ||
                    (t.getNote() != null && t.getNote().toLowerCase().contains(searchQuery.toLowerCase()));

            if (typeMatches && searchMatches) {
                filtered.add(t);
            }
        }

        // 2. Sort according to selection
        Collections.sort(filtered, (o1, o2) -> {
            if (o1.getDate() == null || o2.getDate() == null) return 0;
            
            if ("OLDEST".equals(selectedSortOption)) {
                return o1.getDate().compareTo(o2.getDate());
            } else if ("HIGHEST".equals(selectedSortOption)) {
                return Double.compare(o2.getAmount(), o1.getAmount());
            } else if ("LOWEST".equals(selectedSortOption)) {
                return Double.compare(o1.getAmount(), o2.getAmount());
            } else { // "NEWEST" default
                return o2.getDate().compareTo(o1.getDate());
            }
        });

        // 3. Construct Sectioned List
        List<AdapterItem> adapterItems = new ArrayList<>();
        if ("HIGHEST".equals(selectedSortOption) || "LOWEST".equals(selectedSortOption)) {
            // No date headers if sorting by amount
            for (Transaction t : filtered) {
                adapterItems.add(new AdapterItem(t));
            }
        } else {
            // Group by Date Headers
            String lastHeader = "";
            for (Transaction t : filtered) {
                String headerStr = getHeaderDateString(t.getDate());
                if (!headerStr.equals(lastHeader)) {
                    adapterItems.add(new AdapterItem(headerStr));
                    lastHeader = headerStr;
                }
                adapterItems.add(new AdapterItem(t));
            }
        }

        // 4. Update UI Empty state
        if (adapterItems.isEmpty()) {
            binding.layoutEmptyState.setVisibility(View.VISIBLE);
            binding.rvTransactions.setVisibility(View.GONE);
        } else {
            binding.layoutEmptyState.setVisibility(View.GONE);
            binding.rvTransactions.setVisibility(View.VISIBLE);
        }

        adapter.setItems(adapterItems);
    }

    private void showDeleteConfirmationDialog(Transaction transaction, int position) {
        new AlertDialog.Builder(getContext())
                .setTitle("Delete Transaction")
                .setMessage("Are you sure you want to delete this transaction?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (mAuth.getCurrentUser() != null) {
                        TransactionRepository.getInstance().deleteTransaction(
                                mAuth.getCurrentUser().getUid(),
                                transaction.getId(),
                                aVoid -> Snackbar.make(binding.getRoot(), "Transaction deleted.", Snackbar.LENGTH_SHORT).show(),
                                e -> {
                                    Snackbar.make(binding.getRoot(), "Failed to delete: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
                                    adapter.notifyItemChanged(position);
                                }
                        );
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> adapter.notifyItemChanged(position))
                .setOnCancelListener(dialog -> adapter.notifyItemChanged(position))
                .show();
    }

    private String getHeaderDateString(Date date) {
        if (date == null) return "Unknown Date";

        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(date);
        clearTime(cal1);

        Calendar cal2 = Calendar.getInstance();
        clearTime(cal2);

        if (cal1.equals(cal2)) {
            return "Today";
        }

        cal2.add(Calendar.DAY_OF_YEAR, -1);
        if (cal1.equals(cal2)) {
            return "Yesterday";
        }

        SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy", Locale.US);
        return fmt.format(date);
    }

    private void clearTime(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }

    private class SectionedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final List<AdapterItem> items = new ArrayList<>();
        private final TransactionAdapter.OnItemClickListener clickListener;
        private final SimpleDateFormat itemDateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.US);

        public SectionedAdapter(TransactionAdapter.OnItemClickListener clickListener) {
            this.clickListener = clickListener;
        }

        public void setItems(List<AdapterItem> list) {
            this.items.clear();
            this.items.addAll(list);
            notifyDataSetChanged();
        }

        public AdapterItem getItemAt(int pos) {
            if (pos >= 0 && pos < items.size()) {
                return items.get(pos);
            }
            return null;
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == AdapterItem.TYPE_HEADER) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction_header, parent, false);
                return new HeaderViewHolder(view);
            } else {
                ItemTransactionBinding itemBinding = ItemTransactionBinding.inflate(
                        LayoutInflater.from(parent.getContext()), parent, false);
                return new ItemViewHolder(itemBinding);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            AdapterItem item = items.get(position);
            if (holder.getItemViewType() == AdapterItem.TYPE_HEADER) {
                ((HeaderViewHolder) holder).bind(item.headerTitle);
            } else {
                ((ItemViewHolder) holder).bind(item.transaction);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class HeaderViewHolder extends RecyclerView.ViewHolder {
            private final TextView txtHeader;

            public HeaderViewHolder(@NonNull View itemView) {
                super(itemView);
                txtHeader = itemView.findViewById(R.id.txtHeaderDate);
            }

            public void bind(String title) {
                txtHeader.setText(title);
            }
        }

        class ItemViewHolder extends RecyclerView.ViewHolder {
            private final ItemTransactionBinding itemBinding;

            public ItemViewHolder(ItemTransactionBinding itemBinding) {
                super(itemBinding.getRoot());
                this.itemBinding = itemBinding;
                itemView.setOnClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION && clickListener != null) {
                        clickListener.onItemClick(items.get(pos).transaction);
                    }
                });
            }

            public void bind(Transaction transaction) {
                String noteText = transaction.getNote();
                if (noteText == null || noteText.trim().isEmpty()) {
                    itemBinding.txtTransactionNote.setText(transaction.getCategory());
                } else {
                    itemBinding.txtTransactionNote.setText(noteText);
                }

                String dateStr = transaction.getDate() != null ? itemDateFormat.format(transaction.getDate()) : "";
                itemBinding.txtCategoryAndDate.setText(String.format("%s • %s", transaction.getCategory(), dateStr));

                String formattedAmount = CurrencyManager.getInstance().formatAmount(transaction.getAmount());
                if ("income".equalsIgnoreCase(transaction.getType())) {
                    itemBinding.txtAmount.setText(String.format("+%s", formattedAmount));
                    itemBinding.txtAmount.setTextColor(Color.parseColor("#3DAF85"));
                } else {
                    itemBinding.txtAmount.setText(String.format("-%s", formattedAmount));
                    itemBinding.txtAmount.setTextColor(Color.parseColor("#E24B4A"));
                }

                itemBinding.txtCategoryEmoji.setText(getCategoryEmoji(transaction.getCategory()));
                itemBinding.layoutIconFrame.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#1A7C6FE0")));
            }

            private String getCategoryEmoji(String category) {
                if (category == null) return "📦";
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
}
