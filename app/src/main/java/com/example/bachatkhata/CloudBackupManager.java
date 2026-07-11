package com.example.bachatkhata;

import androidx.annotation.NonNull;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates and restores labeled snapshots of the user's transactions in Firestore under
 * {@code users/{uid}/backups/{backupId}}. Each snapshot embeds a copy of every transaction
 * so it can be restored later, listed, or deleted — independent of the live sync.
 */
public class CloudBackupManager {

    // Firestore has a 500-operation cap per batch; stay comfortably under it.
    private static final int BATCH_LIMIT = 450;

    public static class BackupInfo {
        public final String id;
        public final String label;
        public final long count;
        public final Timestamp createdAt;

        BackupInfo(String id, String label, long count, Timestamp createdAt) {
            this.id = id;
            this.label = label;
            this.count = count;
            this.createdAt = createdAt;
        }
    }

    public interface Callback {
        void onSuccess();
        void onError(String message);
    }

    public interface ListCallback {
        void onLoaded(List<BackupInfo> backups);
        void onError(String message);
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private String uid() {
        return FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
    }

    private CollectionReference transactionsRef(String uid) {
        return db.collection("users").document(uid).collection("transactions");
    }

    private CollectionReference backupsRef(String uid) {
        return db.collection("users").document(uid).collection("backups");
    }

    /** Snapshot every current transaction into a new labeled backup document. */
    public void createBackup(String label, @NonNull Callback cb) {
        String uid = uid();
        if (uid == null) { cb.onError("Not signed in"); return; }

        transactionsRef(uid).get().addOnSuccessListener(snap -> {
            List<Map<String, Object>> txns = new ArrayList<>();
            for (DocumentSnapshot doc : snap.getDocuments()) {
                Map<String, Object> data = doc.getData();
                if (data != null) txns.add(data);
            }

            Map<String, Object> backup = new HashMap<>();
            backup.put("label", (label == null || label.trim().isEmpty()) ? "Backup" : label.trim());
            backup.put("createdAt", Timestamp.now());
            backup.put("count", txns.size());
            backup.put("transactions", txns);

            backupsRef(uid).add(backup)
                    .addOnSuccessListener(ref -> cb.onSuccess())
                    .addOnFailureListener(e -> cb.onError(e.getMessage()));
        }).addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    /** List all backups, newest first. */
    public void listBackups(@NonNull ListCallback cb) {
        String uid = uid();
        if (uid == null) { cb.onError("Not signed in"); return; }

        backupsRef(uid).orderBy("createdAt", Query.Direction.DESCENDING).get()
                .addOnSuccessListener(snap -> {
                    List<BackupInfo> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        String label = doc.getString("label");
                        Long count = doc.getLong("count");
                        Timestamp createdAt = doc.getTimestamp("createdAt");
                        list.add(new BackupInfo(doc.getId(),
                                label != null ? label : "Backup",
                                count != null ? count : 0,
                                createdAt));
                    }
                    cb.onLoaded(list);
                })
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    /** Delete a backup snapshot (does not touch live data). */
    public void deleteBackup(String backupId, @NonNull Callback cb) {
        String uid = uid();
        if (uid == null) { cb.onError("Not signed in"); return; }

        backupsRef(uid).document(backupId).delete()
                .addOnSuccessListener(v -> cb.onSuccess())
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    /**
     * Replace all live transactions with the snapshot's contents: existing transactions are
     * deleted, then the backed-up transactions are written back under their original ids.
     */
    @SuppressWarnings("unchecked")
    public void restoreBackup(String backupId, @NonNull Callback cb) {
        String uid = uid();
        if (uid == null) { cb.onError("Not signed in"); return; }

        backupsRef(uid).document(backupId).get().addOnSuccessListener(backupDoc -> {
            if (!backupDoc.exists()) { cb.onError("Backup not found"); return; }
            List<Map<String, Object>> txns = (List<Map<String, Object>>) backupDoc.get("transactions");
            final List<Map<String, Object>> restoreList = txns != null ? txns : new ArrayList<>();

            // First remove all current transactions, then write the snapshot back.
            transactionsRef(uid).get().addOnSuccessListener(currentSnap -> {
                List<DocumentReference> toDelete = new ArrayList<>();
                for (DocumentSnapshot d : currentSnap.getDocuments()) toDelete.add(d.getReference());

                commitInChunks(toDelete, restoreList, uid, cb);
            }).addOnFailureListener(e -> cb.onError(e.getMessage()));
        }).addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    private void commitInChunks(List<DocumentReference> toDelete,
                                List<Map<String, Object>> toWrite,
                                String uid, Callback cb) {
        // Split delete + write operations into batches of <=BATCH_LIMIT.
        CollectionReference txRef = transactionsRef(uid);

        final List<WriteBatch> batches = new ArrayList<>();
        WriteBatch current = db.batch();
        int opCount = 0;

        for (DocumentReference ref : toDelete) {
            current.delete(ref);
            if (++opCount >= BATCH_LIMIT) { batches.add(current); current = db.batch(); opCount = 0; }
        }
        for (Map<String, Object> data : toWrite) {
            Object id = data.get("id");
            DocumentReference ref = (id instanceof String && !((String) id).isEmpty())
                    ? txRef.document((String) id) : txRef.document();
            current.set(ref, data);
            if (++opCount >= BATCH_LIMIT) { batches.add(current); current = db.batch(); opCount = 0; }
        }
        if (opCount > 0) batches.add(current);

        commitSequentially(batches, 0, cb);
    }

    private void commitSequentially(List<WriteBatch> batches, int index, Callback cb) {
        if (index >= batches.size()) { cb.onSuccess(); return; }
        batches.get(index).commit()
                .addOnSuccessListener(v -> commitSequentially(batches, index + 1, cb))
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }
}
