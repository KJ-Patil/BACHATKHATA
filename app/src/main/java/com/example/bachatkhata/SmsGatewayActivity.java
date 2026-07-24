package com.example.bachatkhata;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;

import com.example.bachatkhata.databinding.ActivitySmsGatewayBinding;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Fast2SMS gateway credentials.
 *
 * <p>Draft-and-Save, not live-write: the fields are a working copy and nothing is
 * persisted until Save is tapped. That matters because the stored key is pulled
 * from the cloud asynchronously — a screen that wrote on every keystroke could
 * push an empty form over a real key before the sync landed.
 */
public class SmsGatewayActivity extends BaseActivity {

    private static final String FAST2SMS_URL = "https://www.fast2sms.com/dashboard/wallet";

    private ActivitySmsGatewayBinding binding;

    /** True once the user edits the key, after which a late sync must not overwrite it. */
    private boolean draftEdited = false;
    private boolean keyRevealed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySmsGatewayBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnOpenFast2Sms.setOnClickListener(v -> openFast2Sms());
        binding.btnRevealKey.setOnClickListener(v -> toggleReveal());
        binding.btnSaveGateway.setOnClickListener(v -> save());

        binding.etApiKey.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) { }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) { }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (!populating) draftEdited = true;
            }
        });

        render(SmsGatewaySettings.get(this));

        // Pull the cloud copy in case this device has never seen the key.
        String uid = currentUid();
        if (uid != null) {
            SmsGatewaySettings.loadFromFirestore(this, uid, () -> {
                if (binding == null || draftEdited) return;
                render(SmsGatewaySettings.get(this));
            });
        }
    }

    /** Guards the TextWatcher while this class is the one writing the field. */
    private boolean populating = false;

    private void render(SmsGatewaySettings.Config config) {
        populating = true;
        binding.etApiKey.setText(config.apiKey);
        populating = false;

        binding.switchGatewayEnabled.setChecked(config.enabled);
        binding.txtStoredKey.setText(config.hasKey()
                ? getString(R.string.gateway_stored_key, SmsGatewaySettings.mask(config.apiKey))
                : getString(R.string.gateway_no_key));
        binding.btnRevealKey.setVisibility(config.hasKey() ? View.VISIBLE : View.GONE);
        applyRevealState();
    }

    private void toggleReveal() {
        keyRevealed = !keyRevealed;
        applyRevealState();
    }

    private void applyRevealState() {
        int selection = binding.etApiKey.getText() == null
                ? 0 : binding.etApiKey.getText().length();

        binding.etApiKey.setInputType(keyRevealed
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        // Changing the input type resets the cursor to the start.
        binding.etApiKey.setSelection(selection);
        binding.btnRevealKey.setText(keyRevealed ? R.string.gateway_hide : R.string.gateway_reveal);
    }

    private void save() {
        String apiKey = binding.etApiKey.getText() == null
                ? "" : binding.etApiKey.getText().toString().trim();
        boolean enabled = binding.switchGatewayEnabled.isChecked();

        // Enabling without a key would store a setting that cannot do anything.
        if (enabled && apiKey.isEmpty()) {
            showSnackbar(getString(R.string.gateway_key_required), "ERROR");
            return;
        }

        SmsGatewaySettings.save(this, currentUid(),
                new SmsGatewaySettings.Config(enabled, apiKey));
        draftEdited = false;

        render(SmsGatewaySettings.get(this));
        showSnackbar(getString(R.string.gateway_saved), "SUCCESS");
    }

    private void openFast2Sms() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(FAST2SMS_URL)));
        } catch (ActivityNotFoundException e) {
            showSnackbar(getString(R.string.help_no_app_for_action), "ERROR");
        }
    }

    private String currentUid() {
        return FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
    }
}
