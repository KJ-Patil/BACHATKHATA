package com.example.bachatkhata;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bachatkhata.databinding.LayoutCurrencyPickerBinding;
import com.example.bachatkhata.databinding.ItemCurrencyRowBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

public class CurrencyPickerBottomSheet extends BottomSheetDialogFragment {

    public interface OnCurrencySelectedListener {
        void onCurrencySelected(CurrencyItem currency);
    }

    public static class CurrencyItem {
        public String flag;
        public String code;
        public String name;
        public String symbol;

        public CurrencyItem(String flag, String code, String name, String symbol) {
            this.flag = flag;
            this.code = code;
            this.name = name;
            this.symbol = symbol;
        }
    }

    private LayoutCurrencyPickerBinding binding;
    private OnCurrencySelectedListener listener;
    private final List<CurrencyItem> allCurrencies = new ArrayList<>();
    private final List<CurrencyItem> filteredCurrencies = new ArrayList<>();
    private CurrencyAdapter adapter;
    private String selectedCode = "INR";

    public static CurrencyPickerBottomSheet newInstance(String selectedCode, OnCurrencySelectedListener listener) {
        CurrencyPickerBottomSheet sheet = new CurrencyPickerBottomSheet();
        sheet.selectedCode = selectedCode;
        sheet.listener = listener;
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = LayoutCurrencyPickerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        populateCurrenciesList();

        binding.rvCurrencies.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new CurrencyAdapter();
        binding.rvCurrencies.setAdapter(adapter);

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void populateCurrenciesList() {
        allCurrencies.clear();
        allCurrencies.add(new CurrencyItem("🇮🇳", "INR", "Indian Rupee", "₹"));
        allCurrencies.add(new CurrencyItem("🇺🇸", "USD", "US Dollar", "$"));
        allCurrencies.add(new CurrencyItem("🇪🇺", "EUR", "Euro", "€"));
        allCurrencies.add(new CurrencyItem("🇬🇧", "GBP", "British Pound", "£"));
        allCurrencies.add(new CurrencyItem("🇯🇵", "JPY", "Japanese Yen", "¥"));
        allCurrencies.add(new CurrencyItem("🇦🇺", "AUD", "Australian Dollar", "A$"));
        allCurrencies.add(new CurrencyItem("🇨🇦", "CAD", "Canadian Dollar", "C$"));
        allCurrencies.add(new CurrencyItem("🇨🇭", "CHF", "Swiss Franc", "Fr"));
        allCurrencies.add(new CurrencyItem("🇨🇳", "CNY", "Chinese Yuan", "¥"));
        allCurrencies.add(new CurrencyItem("🇸🇬", "SGD", "Singapore Dollar", "S$"));
        allCurrencies.add(new CurrencyItem("🇦🇪", "AED", "UAE Dirham", "د.إ"));
        allCurrencies.add(new CurrencyItem("🇸🇦", "SAR", "Saudi Riyal", "﷼"));
        allCurrencies.add(new CurrencyItem("🇰🇼", "KWD", "Kuwaiti Dinar", "د.ك"));
        allCurrencies.add(new CurrencyItem("🇶🇦", "QAR", "Qatari Riyal", "﷼"));
        allCurrencies.add(new CurrencyItem("🇲🇾", "MYR", "Malaysian Ringgit", "RM"));
        allCurrencies.add(new CurrencyItem("🇹🇭", "THB", "Thai Baht", "฿"));
        allCurrencies.add(new CurrencyItem("🇮🇩", "IDR", "Indonesian Rupiah", "Rp"));
        allCurrencies.add(new CurrencyItem("🇵🇭", "PHP", "Philippine Peso", "₱"));
        allCurrencies.add(new CurrencyItem("🇧🇷", "BRL", "Brazilian Real", "R$"));
        allCurrencies.add(new CurrencyItem("🇿🇦", "ZAR", "South African Rand", "R"));

        filteredCurrencies.clear();
        filteredCurrencies.addAll(allCurrencies);
    }

    private void filter(String query) {
        filteredCurrencies.clear();
        if (query.trim().isEmpty()) {
            filteredCurrencies.addAll(allCurrencies);
        } else {
            String lower = query.toLowerCase().trim();
            for (CurrencyItem item : allCurrencies) {
                if (item.code.toLowerCase().contains(lower) || item.name.toLowerCase().contains(lower)) {
                    filteredCurrencies.add(item);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private class CurrencyAdapter extends RecyclerView.Adapter<CurrencyAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemCurrencyRowBinding rowBinding = ItemCurrencyRowBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(rowBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(filteredCurrencies.get(position));
        }

        @Override
        public int getItemCount() {
            return filteredCurrencies.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final ItemCurrencyRowBinding rowBinding;

            public ViewHolder(ItemCurrencyRowBinding rowBinding) {
                super(rowBinding.getRoot());
                this.rowBinding = rowBinding;
            }

            public void bind(CurrencyItem currency) {
                rowBinding.txtFlag.setText(currency.flag);
                rowBinding.txtCurrencyCode.setText(currency.code);
                rowBinding.txtCurrencyName.setText(currency.name);
                rowBinding.txtCurrencySymbol.setText(currency.symbol);

                boolean isSelected = currency.code.equalsIgnoreCase(selectedCode);
                if (isSelected) {
                    rowBinding.cardCurrency.setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#1A7C6FE0"))); // colorPrimary 10%
                    rowBinding.imgCheckmark.setVisibility(View.VISIBLE);
                } else {
                    rowBinding.cardCurrency.setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#FFFFFF"))); // colorSurface light
                    rowBinding.imgCheckmark.setVisibility(View.GONE);
                }

                itemView.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onCurrencySelected(currency);
                    }
                    dismiss();
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
