package com.example.bachatkhata;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bachatkhata.databinding.FragmentCustomerLedgerBinding;
import com.example.bachatkhata.databinding.ItemCustomerBinding;
import com.google.firebase.auth.FirebaseAuth;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CustomerLedgerFragment extends Fragment {

    private FragmentCustomerLedgerBinding binding;
    private CustomerLedgerViewModel viewModel;
    private FirebaseAuth mAuth;
    private CustomerAdapter adapter;

    private final List<Customer> allCustomers = new ArrayList<>();
    private final List<Customer> filteredCustomers = new ArrayList<>();
    private String searchQuery = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentCustomerLedgerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        viewModel = new ViewModelProvider(this).get(CustomerLedgerViewModel.class);

        setupRecyclerView();
        setupListeners();
        observeViewModel();

        if (mAuth.getCurrentUser() != null) {
            viewModel.observeCustomers(mAuth.getCurrentUser().getUid());
        }
    }

    private void setupRecyclerView() {
        adapter = new CustomerAdapter();
        binding.rvCustomers.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvCustomers.setAdapter(adapter);
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().onBackPressed();
            }
        });

        binding.fabAddCustomer.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), AddCustomerActivity.class);
            startActivity(intent);
        });

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString();
                filterCustomers();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void observeViewModel() {
        viewModel.getCustomers().observe(getViewLifecycleOwner(), list -> {
            allCustomers.clear();
            allCustomers.addAll(list);
            filterCustomers();
        });

        viewModel.getTotalToReceive().observe(getViewLifecycleOwner(), val -> 
            binding.txtTotalToReceive.setText(CurrencyManager.getInstance().formatAmount(val))
        );

        viewModel.getTotalToPay().observe(getViewLifecycleOwner(), val -> 
            binding.txtTotalToPay.setText(CurrencyManager.getInstance().formatAmount(val))
        );
    }

    private void filterCustomers() {
        filteredCustomers.clear();
        String query = searchQuery.trim().toLowerCase();

        for (Customer c : allCustomers) {
            boolean matches = query.isEmpty() ||
                    (c.getName() != null && c.getName().toLowerCase().contains(query)) ||
                    (c.getPhone() != null && c.getPhone().toLowerCase().contains(query));

            if (matches) {
                filteredCustomers.add(c);
            }
        }

        if (filteredCustomers.isEmpty()) {
            binding.layoutEmptyState.setVisibility(View.VISIBLE);
            binding.rvCustomers.setVisibility(View.GONE);
        } else {
            binding.layoutEmptyState.setVisibility(View.GONE);
            binding.rvCustomers.setVisibility(View.VISIBLE);
        }

        adapter.notifyDataSetChanged();
    }

    private void openWhatsApp(Customer customer) {
        String phone = customer.getPhone();
        if (phone == null || phone.trim().isEmpty()) return;

        String cleanPhone = phone.replaceAll("[^0-9]", "");
        if (cleanPhone.length() == 10) {
            cleanPhone = "91" + cleanPhone; // default country code for India
        }

        double balance = customer.getBalance();
        String text;
        if (balance > 0) {
            text = String.format(Locale.US,
                    "Hello %s, this is a reminder from BachatKhata that a pending balance of %s is owed to me. Please settle it when possible. Thank you!",
                    customer.getName(),
                    CurrencyManager.getInstance().formatAmount(balance)
            );
        } else if (balance < 0) {
            text = String.format(Locale.US,
                    "Hello %s, please share your details so I can settle the balance of %s that I owe you. Thank you!",
                    customer.getName(),
                    CurrencyManager.getInstance().formatAmount(Math.abs(balance))
            );
        } else {
            text = String.format(Locale.US, "Hello %s, just checking in regarding our accounts. Thank you!", customer.getName());
        }

        try {
            Uri uri = Uri.parse("https://wa.me/" + cleanPhone + "?text=" + URLEncoder.encode(text, "UTF-8"));
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    private class CustomerAdapter extends RecyclerView.Adapter<CustomerAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemCustomerBinding rowBinding = ItemCustomerBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(rowBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(filteredCustomers.get(position));
        }

        @Override
        public int getItemCount() {
            return filteredCustomers.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final ItemCustomerBinding rowBinding;

            public ViewHolder(ItemCustomerBinding rowBinding) {
                super(rowBinding.getRoot());
                this.rowBinding = rowBinding;

                itemView.setOnClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        Intent intent = new Intent(getContext(), CustomerDetailActivity.class);
                        intent.putExtra("customer", filteredCustomers.get(pos));
                        startActivity(intent);
                    }
                });

                rowBinding.btnWhatsApp.setOnClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        openWhatsApp(filteredCustomers.get(pos));
                    }
                });
            }

            public void bind(Customer customer) {
                rowBinding.txtCustomerName.setText(customer.getName());
                rowBinding.txtCustomerPhone.setText(customer.getPhone());

                if (customer.getName() != null && !customer.getName().trim().isEmpty()) {
                    rowBinding.txtAvatarLetter.setText(customer.getName().substring(0, 1).toUpperCase(Locale.US));
                } else {
                    rowBinding.txtAvatarLetter.setText("C");
                }

                double balance = customer.getBalance();
                String formattedBalance = CurrencyManager.getInstance().formatAmount(Math.abs(balance));
                rowBinding.txtCustomerBalance.setText(formattedBalance);

                if (balance > 0) {
                    // Owed to you (Receive) - Green
                    rowBinding.txtCustomerBalance.setTextColor(Color.parseColor("#3DAF85"));
                    rowBinding.txtCustomerBalanceLabel.setText("You will get");
                    rowBinding.txtCustomerBalanceLabel.setTextColor(Color.parseColor("#3DAF85"));
                    rowBinding.layoutAvatarBg.setBackgroundColor(Color.parseColor("#1A3DAF85")); // 10% green
                    rowBinding.txtAvatarLetter.setTextColor(Color.parseColor("#3DAF85"));
                } else if (balance < 0) {
                    // You owe them (Pay) - Red
                    rowBinding.txtCustomerBalance.setTextColor(Color.parseColor("#E24B4A"));
                    rowBinding.txtCustomerBalanceLabel.setText("You will pay");
                    rowBinding.txtCustomerBalanceLabel.setTextColor(Color.parseColor("#E24B4A"));
                    rowBinding.layoutAvatarBg.setBackgroundColor(Color.parseColor("#1AE24B4A")); // 10% red
                    rowBinding.txtAvatarLetter.setTextColor(Color.parseColor("#E24B4A"));
                } else {
                    // Settled - Gray
                    rowBinding.txtCustomerBalance.setTextColor(Color.parseColor("#1A1A2E"));
                    rowBinding.txtCustomerBalanceLabel.setText("Settled");
                    rowBinding.txtCustomerBalanceLabel.setTextColor(Color.parseColor("#6B6B8A"));
                    rowBinding.layoutAvatarBg.setBackgroundColor(Color.parseColor("#E0DEFF")); // border color
                    rowBinding.txtAvatarLetter.setTextColor(Color.parseColor("#7C6FE0"));
                }
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
