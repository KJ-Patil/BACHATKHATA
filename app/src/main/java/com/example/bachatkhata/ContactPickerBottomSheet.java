package com.example.bachatkhata;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bachatkhata.databinding.ItemContactPickBinding;
import com.example.bachatkhata.databinding.SheetContactPickerBinding;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reusable bottom sheet that reads the device contact book directly (READ_CONTACTS)
 * and shows an in-app, searchable list. Supports single-select (returns one contact)
 * or multi-select (returns several at once).
 */
public class ContactPickerBottomSheet extends BottomSheetDialogFragment {

    public static class Contact {
        public final String name;
        public final String phone;

        public Contact(String name, String phone) {
            this.name = name;
            this.phone = phone;
        }
    }

    public interface OnContactsPickedListener {
        void onContactsPicked(List<Contact> contacts);
    }

    private static final String ARG_MULTI = "multi";

    private SheetContactPickerBinding binding;
    private boolean multiSelect;
    private OnContactsPickedListener listener;

    private final List<Contact> allContacts = new ArrayList<>();
    private final List<Contact> filtered = new ArrayList<>();
    private final Set<Contact> selected = new LinkedHashSet<>();
    private ContactAdapter adapter;

    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                if (binding == null) return;
                if (granted) {
                    loadContacts();
                } else {
                    showPermissionState();
                }
            });

    public static ContactPickerBottomSheet newInstance(boolean multiSelect) {
        ContactPickerBottomSheet f = new ContactPickerBottomSheet();
        Bundle args = new Bundle();
        args.putBoolean(ARG_MULTI, multiSelect);
        f.setArguments(args);
        return f;
    }

    public void setListener(OnContactsPickedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        multiSelect = getArguments() != null && getArguments().getBoolean(ARG_MULTI, false);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = SheetContactPickerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        // Make the sheet a tall, fixed-height scrollable panel.
        if (getDialog() != null) {
            View sheet = getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                sheet.getLayoutParams().height = (int) (getResources().getDisplayMetrics().heightPixels * 0.85f);
                sheet.requestLayout();
                BottomSheetBehavior.from(sheet).setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.txtPickerTitle.setText(multiSelect ? "Select Contacts" : "Select a Contact");
        binding.btnAddSelected.setVisibility(multiSelect ? View.VISIBLE : View.GONE);

        adapter = new ContactAdapter();
        binding.rvContacts.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvContacts.setAdapter(adapter);

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { applyFilter(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        binding.btnGrant.setOnClickListener(v -> requestPermission());
        binding.btnAddSelected.setOnClickListener(v -> deliver(new ArrayList<>(selected)));

        if (hasPermission()) {
            loadContacts();
        } else {
            requestPermission();
        }
    }

    private boolean hasPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        permissionLauncher.launch(Manifest.permission.READ_CONTACTS);
    }

    private void showPermissionState() {
        binding.layoutPermission.setVisibility(View.VISIBLE);
        binding.rvContacts.setVisibility(View.GONE);
        binding.progressBar.setVisibility(View.GONE);
        binding.txtEmpty.setVisibility(View.GONE);
        binding.btnAddSelected.setVisibility(View.GONE);
    }

    private void loadContacts() {
        binding.layoutPermission.setVisibility(View.GONE);
        binding.txtEmpty.setVisibility(View.GONE);
        binding.progressBar.setVisibility(View.VISIBLE);

        final Context appContext = requireContext().getApplicationContext();
        new Thread(() -> {
            List<Contact> loaded = queryContacts(appContext);
            if (!isAdded() || getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                if (binding == null) return;
                binding.progressBar.setVisibility(View.GONE);
                allContacts.clear();
                allContacts.addAll(loaded);
                if (multiSelect) {
                    binding.btnAddSelected.setVisibility(View.VISIBLE);
                }
                applyFilter(binding.etSearch.getText() != null ? binding.etSearch.getText().toString() : "");
            });
        }).start();
    }

    private List<Contact> queryContacts(Context context) {
        List<Contact> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };
        try (Cursor cursor = context.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection, null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")) {
            if (cursor != null) {
                int nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                while (cursor.moveToNext()) {
                    String name = nameIdx >= 0 ? cursor.getString(nameIdx) : null;
                    String number = numIdx >= 0 ? cursor.getString(numIdx) : null;
                    if (number == null) continue;
                    String cleanNumber = number.replaceAll("[\\s\\-()]", "");
                    if (cleanNumber.isEmpty()) continue;
                    // Dedupe on number so the same person doesn't repeat.
                    if (!seen.add(cleanNumber)) continue;
                    result.add(new Contact(name != null ? name : cleanNumber, cleanNumber));
                }
            }
        } catch (Exception ignored) {
            // Return whatever was collected before the failure.
        }
        return result;
    }

    private void applyFilter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.getDefault());
        filtered.clear();
        for (Contact c : allContacts) {
            boolean match = q.isEmpty()
                    || (c.name != null && c.name.toLowerCase(Locale.getDefault()).contains(q))
                    || (c.phone != null && c.phone.contains(q));
            if (match) filtered.add(c);
        }
        adapter.notifyDataSetChanged();
        binding.txtEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        binding.rvContacts.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void deliver(List<Contact> contacts) {
        if (contacts.isEmpty()) {
            dismiss();
            return;
        }
        if (listener != null) listener.onContactsPicked(contacts);
        dismiss();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemContactPickBinding b = ItemContactPickBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new Holder(b);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.bind(filtered.get(position));
        }

        @Override
        public int getItemCount() {
            return filtered.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final ItemContactPickBinding b;

            Holder(ItemContactPickBinding b) {
                super(b.getRoot());
                this.b = b;
            }

            void bind(Contact c) {
                b.txtName.setText(c.name);
                b.txtPhone.setText(c.phone);
                b.txtAvatar.setText(c.name != null && !c.name.trim().isEmpty()
                        ? c.name.substring(0, 1).toUpperCase(Locale.getDefault()) : "?");

                if (multiSelect) {
                    b.chkSelect.setVisibility(View.VISIBLE);
                    b.chkSelect.setChecked(selected.contains(c));
                    itemView.setOnClickListener(v -> {
                        if (selected.contains(c)) selected.remove(c);
                        else selected.add(c);
                        b.chkSelect.setChecked(selected.contains(c));
                        binding.btnAddSelected.setText(selected.isEmpty()
                                ? "Add Selected" : "Add Selected (" + selected.size() + ")");
                    });
                } else {
                    b.chkSelect.setVisibility(View.GONE);
                    itemView.setOnClickListener(v -> {
                        List<Contact> one = new ArrayList<>();
                        one.add(c);
                        deliver(one);
                    });
                }
            }
        }
    }
}
