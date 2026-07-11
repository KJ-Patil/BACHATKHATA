package com.example.bachatkhata;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bachatkhata.databinding.ActivityCloudBackupBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Screen for creating, listing, restoring and deleting labeled cloud backup snapshots.
 * Backend logic lives in {@link CloudBackupManager}.
 */
public class CloudBackupActivity extends BaseActivity {

    private ActivityCloudBackupBinding binding;
    private final CloudBackupManager manager = new CloudBackupManager();
    private final List<CloudBackupManager.BackupInfo> backups = new ArrayList<>();
    private BackupAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCloudBackupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnCreateBackup.setOnClickListener(v -> promptCreateBackup());

        adapter = new BackupAdapter();
        binding.rvBackups.setLayoutManager(new LinearLayoutManager(this));
        binding.rvBackups.setAdapter(adapter);

        loadBackups();
    }

    private void loadBackups() {
        showLoadingDialog();
        manager.listBackups(new CloudBackupManager.ListCallback() {
            @Override
            public void onLoaded(List<CloudBackupManager.BackupInfo> list) {
                hideLoadingDialog();
                backups.clear();
                backups.addAll(list);
                adapter.notifyDataSetChanged();
                binding.tvEmpty.setVisibility(backups.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(String message) {
                hideLoadingDialog();
                showSnackbar("Couldn't load backups: " + message, "ERROR");
            }
        });
    }

    private void promptCreateBackup() {
        EditText input = new EditText(this);
        input.setHint(R.string.backup_label_hint);
        int pad = Math.round(getResources().getDisplayMetrics().density * 20);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.backup_create)
                .setView(input, pad, pad / 2, pad, 0)
                .setPositiveButton(R.string.backup_create, (d, w) -> createBackup(input.getText().toString()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void createBackup(String label) {
        showLoadingDialog();
        manager.createBackup(label, new CloudBackupManager.Callback() {
            @Override
            public void onSuccess() {
                hideLoadingDialog();
                showSnackbar("Backup created", "SUCCESS");
                loadBackups();
            }

            @Override
            public void onError(String message) {
                hideLoadingDialog();
                showSnackbar("Backup failed: " + message, "ERROR");
            }
        });
    }

    private void confirmRestore(CloudBackupManager.BackupInfo info) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.backup_restore)
                .setMessage(R.string.backup_restore_confirm)
                .setPositiveButton(R.string.backup_restore, (d, w) -> {
                    showLoadingDialog();
                    manager.restoreBackup(info.id, new CloudBackupManager.Callback() {
                        @Override
                        public void onSuccess() {
                            hideLoadingDialog();
                            showSnackbar("Backup restored", "SUCCESS");
                        }

                        @Override
                        public void onError(String message) {
                            hideLoadingDialog();
                            showSnackbar("Restore failed: " + message, "ERROR");
                        }
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmDelete(CloudBackupManager.BackupInfo info) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.backup_delete)
                .setMessage(R.string.backup_delete_confirm)
                .setPositiveButton(R.string.backup_delete, (d, w) -> {
                    showLoadingDialog();
                    manager.deleteBackup(info.id, new CloudBackupManager.Callback() {
                        @Override
                        public void onSuccess() {
                            hideLoadingDialog();
                            loadBackups();
                        }

                        @Override
                        public void onError(String message) {
                            hideLoadingDialog();
                            showSnackbar("Delete failed: " + message, "ERROR");
                        }
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // --- Adapter ---

    private class BackupAdapter extends RecyclerView.Adapter<BackupAdapter.VH> {
        private final SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_backup, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            CloudBackupManager.BackupInfo info = backups.get(position);
            holder.label.setText(info.label);
            String date = info.createdAt != null ? fmt.format(info.createdAt.toDate()) : "";
            holder.meta.setText(info.count + " transactions • " + date);
            holder.btnRestore.setOnClickListener(v -> confirmRestore(info));
            holder.btnDelete.setOnClickListener(v -> confirmDelete(info));
        }

        @Override
        public int getItemCount() {
            return backups.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView label, meta;
            final View btnRestore, btnDelete;

            VH(@NonNull View itemView) {
                super(itemView);
                label = itemView.findViewById(R.id.tvLabel);
                meta = itemView.findViewById(R.id.tvMeta);
                btnRestore = itemView.findViewById(R.id.btnRestore);
                btnDelete = itemView.findViewById(R.id.btnDelete);
            }
        }
    }
}
