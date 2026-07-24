package com.example.bachatkhata;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bachatkhata.databinding.FragmentCustomerLedgerBinding;
import com.example.bachatkhata.databinding.ItemCustomerBinding;
import com.example.bachatkhata.domain.ReminderService;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
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
    private int activeTab = 0; // 0=All, 1=Credits, 2=Debits, 3=Settled

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCustomerLedgerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        viewModel = new ViewModelProvider(this).get(CustomerLedgerViewModel.class);

        setupRecyclerView();
        setupTabs();
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

    private void setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("ALL BOOKS (0)"));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("CREDITS (0)"));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("DEBITS (0)"));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("SETTLED (0)"));

        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                activeTab = tab.getPosition();
                filterCustomers();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void updateTabCounts(int total, int credits, int debits, int settled) {
        if (binding.tabLayout.getTabAt(0) != null)
            binding.tabLayout.getTabAt(0).setText("ALL BOOKS (" + total + ")");
        if (binding.tabLayout.getTabAt(1) != null)
            binding.tabLayout.getTabAt(1).setText("CREDITS (" + credits + ")");
        if (binding.tabLayout.getTabAt(2) != null)
            binding.tabLayout.getTabAt(2).setText("DEBITS (" + debits + ")");
        if (binding.tabLayout.getTabAt(3) != null)
            binding.tabLayout.getTabAt(3).setText("SETTLED (" + settled + ")");
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> {
            if (getActivity() != null) getActivity().onBackPressed();
        });

        binding.btnAddAccount.setOnClickListener(v -> {
            AddAccountBookBottomSheet sheet = new AddAccountBookBottomSheet();
            sheet.show(getChildFragmentManager(), "AddAccountBook");
        });

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString();
                filterCustomers();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void observeViewModel() {
        viewModel.getCustomers().observe(getViewLifecycleOwner(), list -> {
            allCustomers.clear();
            allCustomers.addAll(list);

            int credits = 0, debits = 0, settled = 0;
            for (Customer c : list) {
                double b = c.getBalance();
                if (b > 0) credits++;
                else if (b < 0) debits++;
                else settled++;
            }
            updateTabCounts(list.size(), credits, debits, settled);
            filterCustomers();
        });

        viewModel.getTotalToReceive().observe(getViewLifecycleOwner(), val ->
            binding.txtTotalToReceive.setText(CurrencyManager.getInstance().formatAmount(val))
        );

        viewModel.getTotalToPay().observe(getViewLifecycleOwner(), val ->
            binding.txtTotalToPay.setText(CurrencyManager.getInstance().formatAmount(val))
        );

        viewModel.getNetPosition().observe(getViewLifecycleOwner(), val -> {
            String prefix = val >= 0 ? "+" : "";
            binding.txtNetPosition.setText(prefix + CurrencyManager.getInstance().formatAmount(Math.abs(val)));
            binding.txtNetPosition.setTextColor(val >= 0
                    ? Color.parseColor("#3DAF85")
                    : Color.parseColor("#E24B4A"));
        });
    }

    private void filterCustomers() {
        filteredCustomers.clear();
        String query = searchQuery.trim().toLowerCase(Locale.US);

        for (Customer c : allCustomers) {
            double balance = c.getBalance();
            boolean tabMatch;
            switch (activeTab) {
                case 1: tabMatch = balance > 0; break;
                case 2: tabMatch = balance < 0; break;
                case 3: tabMatch = balance == 0; break;
                default: tabMatch = true; break;
            }

            boolean searchMatch = query.isEmpty()
                    || (c.getName() != null && c.getName().toLowerCase(Locale.US).contains(query))
                    || (c.getPhone() != null && c.getPhone().toLowerCase(Locale.US).contains(query));

            if (tabMatch && searchMatch) {
                filteredCustomers.add(c);
            }
        }

        boolean empty = filteredCustomers.isEmpty();
        binding.layoutEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.rvCustomers.setVisibility(empty ? View.GONE : View.VISIBLE);
        adapter.notifyDataSetChanged();
    }

    /** Quick reminder trigger from a ledger row — opens the shared composer. */
    private void openWhatsApp(Customer customer) {
        String phone = customer.getPhone();
        if (phone == null || phone.trim().isEmpty()) {
            Toast.makeText(getContext(), R.string.reminder_no_phone, Toast.LENGTH_SHORT).show();
            return;
        }

        double balance = customer.getBalance();
        if (balance == 0) {
            Toast.makeText(getContext(), R.string.reminder_balance_settled, Toast.LENGTH_SHORT).show();
            return;
        }

        ReminderService.Relation relation = balance > 0
                ? ReminderService.Relation.CREDIT
                : ReminderService.Relation.DEBIT;

        FlashReminderBottomSheet.newInstance(
                        customer.getName(),
                        phone,
                        CurrencyManager.getInstance().formatAmount(Math.abs(balance)),
                        relation)
                .show(getParentFragmentManager(), "FlashReminderBottomSheet");
    }

    static String formatLastActivity(com.google.firebase.Timestamp ts) {
        if (ts == null) return "";
        Date date = ts.toDate();
        Calendar now = Calendar.getInstance();
        Calendar activity = Calendar.getInstance();
        activity.setTime(date);

        if (now.get(Calendar.YEAR) == activity.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == activity.get(Calendar.DAY_OF_YEAR)) {
            return "Today";
        }
        now.add(Calendar.DAY_OF_YEAR, -1);
        if (now.get(Calendar.YEAR) == activity.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == activity.get(Calendar.DAY_OF_YEAR)) {
            return "Yesterday";
        }
        return new SimpleDateFormat("MMM d", Locale.US).format(date);
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
            private final ItemCustomerBinding b;

            ViewHolder(ItemCustomerBinding b) {
                super(b.getRoot());
                this.b = b;

                itemView.setOnClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        Intent intent = new Intent(getContext(), CustomerDetailActivity.class);
                        intent.putExtra("customer", filteredCustomers.get(pos));
                        startActivity(intent);
                    }
                });

                b.btnWhatsApp.setOnClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) openWhatsApp(filteredCustomers.get(pos));
                });
            }

            void bind(Customer customer) {
                b.txtCustomerName.setText(customer.getName());
                b.txtCustomerPhone.setText(
                        customer.getPhone() != null && !customer.getPhone().isEmpty()
                                ? customer.getPhone() : "—");
                b.txtLastActivity.setText(formatLastActivity(customer.getCreatedAt()));

                if (customer.getName() != null && !customer.getName().trim().isEmpty()) {
                    b.txtAvatarLetter.setText(
                            customer.getName().substring(0, 1).toUpperCase(Locale.US));
                } else {
                    b.txtAvatarLetter.setText("?");
                }

                // Type badge
                boolean isCustomer = "customer".equals(customer.getType());
                int badgeColor = isCustomer
                        ? Color.parseColor("#3DAF85")
                        : Color.parseColor("#EF9F27");
                float r = 20f * itemView.getContext().getResources().getDisplayMetrics().density;
                GradientDrawable badgeBg = new GradientDrawable();
                badgeBg.setShape(GradientDrawable.RECTANGLE);
                badgeBg.setCornerRadius(r);
                badgeBg.setColor(Color.argb(40,
                        Color.red(badgeColor), Color.green(badgeColor), Color.blue(badgeColor)));
                b.txtTypeBadge.setBackground(badgeBg);
                b.txtTypeBadge.setText(isCustomer ? "CUSTOMER" : "SUPPLIER");
                b.txtTypeBadge.setTextColor(badgeColor);

                // Balance
                double balance = customer.getBalance();
                b.txtCustomerBalance.setText(
                        CurrencyManager.getInstance().formatAmount(Math.abs(balance)));

                if (balance > 0) {
                    b.txtCustomerBalance.setTextColor(Color.parseColor("#3DAF85"));
                    b.txtCustomerBalanceLabel.setText("YOU WILL GET");
                    b.txtCustomerBalanceLabel.setTextColor(Color.parseColor("#3DAF85"));
                    b.layoutAvatarBg.setBackgroundColor(Color.parseColor("#1A3DAF85"));
                    b.txtAvatarLetter.setTextColor(Color.parseColor("#3DAF85"));
                } else if (balance < 0) {
                    b.txtCustomerBalance.setTextColor(Color.parseColor("#E24B4A"));
                    b.txtCustomerBalanceLabel.setText("YOU WILL GIVE");
                    b.txtCustomerBalanceLabel.setTextColor(Color.parseColor("#E24B4A"));
                    b.layoutAvatarBg.setBackgroundColor(Color.parseColor("#1AE24B4A"));
                    b.txtAvatarLetter.setTextColor(Color.parseColor("#E24B4A"));
                } else {
                    b.txtCustomerBalance.setTextColor(Color.parseColor("#1A1A2E"));
                    b.txtCustomerBalanceLabel.setText("SETTLED");
                    b.txtCustomerBalanceLabel.setTextColor(Color.parseColor("#6B6B8A"));
                    b.layoutAvatarBg.setBackgroundColor(Color.parseColor("#E0DEFF"));
                    b.txtAvatarLetter.setTextColor(Color.parseColor("#7C6FE0"));
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
