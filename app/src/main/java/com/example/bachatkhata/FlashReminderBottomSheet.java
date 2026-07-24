package com.example.bachatkhata;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bachatkhata.databinding.SheetFlashReminderBinding;
import com.example.bachatkhata.domain.ReminderService;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * Reminder composer shared by the Ledger, Ledger Detail and Bill Splitter screens.
 *
 * <p>Tone × language × relation picks a template; the user can edit the draft
 * before sending. Only two dispatch channels exist and both are real handoffs to
 * another app — nothing here reports a message as "sent", because the app has no
 * way to know that.
 */
public class FlashReminderBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_NAME = "name";
    private static final String ARG_PHONE = "phone";
    private static final String ARG_AMOUNT = "amount";
    private static final String ARG_RELATION = "relation";

    private SheetFlashReminderBinding binding;

    /** True once the user edits the draft, after which regenerating would discard typing. */
    private boolean draftEdited = false;

    public static FlashReminderBottomSheet newInstance(String name, String phone,
                                                       String formattedAmount,
                                                       ReminderService.Relation relation) {
        FlashReminderBottomSheet sheet = new FlashReminderBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_NAME, name);
        args.putString(ARG_PHONE, phone);
        args.putString(ARG_AMOUNT, formattedAmount);
        args.putString(ARG_RELATION, relation.name());
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetFlashReminderBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.txtReminderSubtitle.setText(getString(R.string.reminder_subtitle, name(), amount()));

        // Changing tone or language rebuilds the draft, but only while it is still
        // the generated one — otherwise a stray chip tap would wipe the user's edits.
        binding.chipGroupTone.setOnCheckedStateChangeListener((group, ids) -> regenerate());
        binding.chipGroupLang.setOnCheckedStateChangeListener((group, ids) -> regenerate());

        binding.etReminderMessage.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) { }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) { }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (!regenerating) draftEdited = true;
            }
        });

        binding.btnSendWhatsApp.setOnClickListener(v -> send(
                ReminderService.whatsAppUrl(phone(), draft()), R.string.reminder_no_whatsapp));
        binding.btnSendSms.setOnClickListener(v -> send(
                ReminderService.smsUri(phone(), draft()), R.string.reminder_no_sms));

        regenerate();
    }

    /** Guards the TextWatcher while this class is the one writing the field. */
    private boolean regenerating = false;

    private void regenerate() {
        if (draftEdited) return;

        String message = ReminderService.generate(name(), amount(), selectedTone(),
                selectedLang(), relation());

        regenerating = true;
        binding.etReminderMessage.setText(message);
        regenerating = false;
    }

    private ReminderService.Tone selectedTone() {
        int checked = binding.chipGroupTone.getCheckedChipId();
        if (checked == R.id.chipToneFormal) return ReminderService.Tone.FORMAL;
        if (checked == R.id.chipToneUrgent) return ReminderService.Tone.URGENT;
        return ReminderService.Tone.FRIENDLY;
    }

    private ReminderService.Lang selectedLang() {
        int checked = binding.chipGroupLang.getCheckedChipId();
        if (checked == R.id.chipLangHi) return ReminderService.Lang.HI;
        if (checked == R.id.chipLangMr) return ReminderService.Lang.MR;
        return ReminderService.Lang.EN;
    }

    private void send(String uri, int failureMessage) {
        String phone = phone();
        if (phone == null || phone.trim().isEmpty()) {
            Toast.makeText(getContext(), R.string.reminder_no_phone, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
            dismiss();
        } catch (ActivityNotFoundException e) {
            Toast.makeText(getContext(), failureMessage, Toast.LENGTH_LONG).show();
        }
    }

    private String draft() {
        return binding.etReminderMessage.getText() == null
                ? "" : binding.etReminderMessage.getText().toString();
    }

    private String name() {
        return getArguments() == null ? "" : getArguments().getString(ARG_NAME, "");
    }

    private String phone() {
        return getArguments() == null ? "" : getArguments().getString(ARG_PHONE, "");
    }

    private String amount() {
        return getArguments() == null ? "" : getArguments().getString(ARG_AMOUNT, "");
    }

    private ReminderService.Relation relation() {
        String raw = getArguments() == null
                ? null : getArguments().getString(ARG_RELATION);
        try {
            return raw == null
                    ? ReminderService.Relation.CREDIT
                    : ReminderService.Relation.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return ReminderService.Relation.CREDIT;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
