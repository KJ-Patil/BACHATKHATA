package com.example.bachatkhata;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bachatkhata.databinding.ActivityHelpBinding;
import com.example.bachatkhata.databinding.ItemFaqBinding;
import com.example.bachatkhata.domain.ReminderService;

import java.util.ArrayList;
import java.util.List;

/**
 * Searchable FAQ, plus support contact actions.
 *
 * <p>The contact cards render only when a channel is actually configured in
 * {@code local.properties} — a placeholder number shipped as a live "call us"
 * button is worse than no button at all.
 */
public class HelpActivity extends BaseActivity {

    private ActivityHelpBinding binding;
    private FaqAdapter adapter;

    private final List<FaqCatalog.Entry> allEntries = new ArrayList<>();
    private final List<FaqCatalog.Entry> visibleEntries = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHelpBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());

        allEntries.addAll(FaqCatalog.all(this));
        visibleEntries.addAll(allEntries);

        adapter = new FaqAdapter();
        binding.rvFaq.setAdapter(adapter);

        binding.etFaqSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                applySearch(s.toString());
            }
        });

        setupContactChannels();
    }

    private void applySearch(String query) {
        visibleEntries.clear();
        visibleEntries.addAll(FaqCatalog.search(allEntries, query));
        adapter.notifyDataSetChanged();

        boolean empty = visibleEntries.isEmpty();
        binding.txtFaqEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.rvFaq.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    /**
     * Shows only the channels that have a value behind them. If none are
     * configured the whole block stays hidden and the screen is FAQ-only.
     */
    private void setupContactChannels() {
        String phone = BuildConfig.SUPPORT_PHONE;
        String email = BuildConfig.SUPPORT_EMAIL;

        boolean hasPhone = phone != null && !phone.trim().isEmpty();
        boolean hasEmail = email != null && !email.trim().isEmpty();

        if (hasPhone) {
            binding.btnContactWhatsApp.setVisibility(View.VISIBLE);
            binding.btnContactWhatsApp.setOnClickListener(v -> open(
                    ReminderService.whatsAppUrl(phone, getString(R.string.help_whatsapp_prefill))));

            binding.btnContactCall.setVisibility(View.VISIBLE);
            binding.btnContactCall.setOnClickListener(v -> open("tel:" + phone.trim()));
        }

        if (hasEmail) {
            binding.btnContactEmail.setVisibility(View.VISIBLE);
            binding.btnContactEmail.setOnClickListener(v -> open("mailto:" + email.trim()));
        }

        binding.layoutContactCards.setVisibility(hasPhone || hasEmail ? View.VISIBLE : View.GONE);
    }

    private void open(String uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
        } catch (ActivityNotFoundException e) {
            showSnackbar(getString(R.string.help_no_app_for_action), "ERROR");
        }
    }

    /** Accordion list — tapping a row expands its answer in place. */
    private class FaqAdapter extends RecyclerView.Adapter<FaqAdapter.FaqViewHolder> {

        @NonNull
        @Override
        public FaqViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new FaqViewHolder(ItemFaqBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull FaqViewHolder holder, int position) {
            holder.bind(visibleEntries.get(position));
        }

        @Override
        public int getItemCount() {
            return visibleEntries.size();
        }

        class FaqViewHolder extends RecyclerView.ViewHolder {
            private final ItemFaqBinding itemBinding;

            FaqViewHolder(ItemFaqBinding itemBinding) {
                super(itemBinding.getRoot());
                this.itemBinding = itemBinding;
            }

            void bind(FaqCatalog.Entry entry) {
                itemBinding.txtFaqQuestion.setText(entry.question);
                itemBinding.txtFaqAnswer.setText(entry.answer);
                applyExpandedState(entry);

                itemBinding.getRoot().setOnClickListener(v -> {
                    entry.expanded = !entry.expanded;
                    applyExpandedState(entry);
                });
            }

            private void applyExpandedState(FaqCatalog.Entry entry) {
                itemBinding.txtFaqAnswer.setVisibility(entry.expanded ? View.VISIBLE : View.GONE);
                itemBinding.txtFaqChevron.setText(entry.expanded ? "−" : "+");
            }
        }
    }
}
