package com.example.bachatkhata;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
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
 * Creates and restores labeled snapshots of the user's financial data under
 * {@code users/{uid}/backups/{backupId}}.
 *
 * <p>A backup covers <b>every</b> financial collection, not just transactions:
 * budgets, goals, categories, the ledger, loans, subscriptions and bills. Each
 * collection is stored in its own child document
 * {@code backups/{id}/data/{collection}} (as an {@code items} array), rather than
 * all in one parent doc — that keeps each document well under Firestore's 1&nbsp;MiB
 * cap and lets a restore touch collections independently.
 *
 * <p><b>Why the scope matters:</b> a restore replaces live data. When backups
 * held only transactions, restoring an old one still deleted the live
 * transactions while leaving budgets/goals stranded and unrecoverable. Backing up
 * the full set keeps restore coherent.
 */
public class CloudBackupManager {

    // Firestore caps a batch at 500 operations; stay comfortably under.
    private static final int BATCH_LIMIT = 450;

    /**
     * Financial collections included in a backup. This must cover everything a
     * restore or a Clear-All-Data could destroy — if a collection is wiped
     * somewhere but never backed up here, that data is unrecoverable. It is a
     * superset of the financial collections {@code ProfileFragment.clearAllUserData}
     * clears (it also adds emis/bills/subscriptions); ephemeral, non-financial
     * data like notifications is deliberately left out.
     */
    static final String[] BACKED_UP_COLLECTIONS = {
            "transactions", "budgets", "savings_goals", "categories",
            "customers", "customer_txns", "emis", "bills", "subscriptions"
    };

    // Money-rule config lives on the user doc, not in a collection, so it is
    // carried in the backup's metadata and restored from there.
    private static final String FIELD_MONTHLY_INCOME = "monthlyIncome";
    private static final String FIELD_RULE_NEEDS = "ruleNeeds";
    private static final String FIELD_RULE_WANTS = "ruleWants";
    private static final String FIELD_RULE_INVESTMENTS = "ruleInvestments";

    public static class BackupInfo {
        public final String id;
        public final String label;
        public final long count;   // total items across all collections
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

    private CollectionReference backupsRef(String uid) {
        return db.collection("users").document(uid).collection("backups");
    }

    // ── Create ──────────────────────────────────────────────────────────────

    /** Snapshot every financial collection into a new labeled backup. */
    public void createBackup(String label, @NonNull Callback cb) {
        String uid = uid();
        if (uid == null) { cb.onError("Not signed in"); return; }
        createBackupInternal(uid, label, false, cb);
    }

    private void createBackupInternal(String uid, String label, boolean automatic, @NonNull Callback cb) {
        DocumentReference userRef = db.collection("users").document(uid);

        // Fetch every collection plus the user doc (for the money-rule config) in
        // parallel, then assemble one backup from the results.
        List<Task<?>> fetches = new ArrayList<>();
        fetches.add(userRef.get());
        for (String collection : BACKED_UP_COLLECTIONS) {
            fetches.add(userRef.collection(collection).get());
        }

        Tasks.whenAllComplete(fetches).addOnCompleteListener(t -> {
            try {
                String backupId = backupsRef(uid).document().getId();
                DocumentReference backupRef = backupsRef(uid).document(backupId);

                // Index 0 is the user doc; the rest line up with BACKED_UP_COLLECTIONS.
                DocumentSnapshot userDoc = (DocumentSnapshot) fetches.get(0).getResult();

                WriteBatch batch = db.batch();
                int ops = 0;
                long total = 0;
                List<Task<Void>> commits = new ArrayList<>();
                Map<String, Object> countsByCollection = new HashMap<>();

                for (int i = 0; i < BACKED_UP_COLLECTIONS.length; i++) {
                    String collection = BACKED_UP_COLLECTIONS[i];
                    com.google.firebase.firestore.QuerySnapshot snap =
                            (com.google.firebase.firestore.QuerySnapshot) fetches.get(i + 1).getResult();

                    List<Map<String, Object>> items = new ArrayList<>();
                    if (snap != null) {
                        for (DocumentSnapshot d : snap.getDocuments()) {
                            Map<String, Object> data = d.getData();
                            if (data != null) items.add(data);
                        }
                    }
                    total += items.size();
                    countsByCollection.put(collection, items.size());

                    // One child doc per collection: backups/{id}/data/{collection}.
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("items", items);
                    batch.set(backupRef.collection("data").document(collection), payload);
                    if (++ops >= BATCH_LIMIT) { commits.add(batch.commit()); batch = db.batch(); ops = 0; }
                }

                // Parent metadata doc, including the money-rule config.
                Map<String, Object> meta = new HashMap<>();
                meta.put("label", (label == null || label.trim().isEmpty())
                        ? (automatic ? "Auto-backup (before restore)" : "Backup") : label.trim());
                meta.put("createdAt", Timestamp.now());
                meta.put("automatic", automatic);
                meta.put("count", total);
                meta.put("counts", countsByCollection);
                meta.put("collections", new ArrayList<>(java.util.Arrays.asList(BACKED_UP_COLLECTIONS)));
                if (userDoc != null && userDoc.exists()) {
                    copyIfPresent(userDoc, meta, FIELD_MONTHLY_INCOME);
                    copyIfPresent(userDoc, meta, FIELD_RULE_NEEDS);
                    copyIfPresent(userDoc, meta, FIELD_RULE_WANTS);
                    copyIfPresent(userDoc, meta, FIELD_RULE_INVESTMENTS);
                }
                batch.set(backupRef, meta);
                commits.add(batch.commit());

                Tasks.whenAll(commits)
                        .addOnSuccessListener(v -> cb.onSuccess())
                        .addOnFailureListener(e -> cb.onError(e.getMessage()));
            } catch (RuntimeException e) {
                cb.onError(e.getMessage());
            }
        });
    }

    private void copyIfPresent(DocumentSnapshot doc, Map<String, Object> into, String field) {
        Object value = doc.get(field);
        if (value != null) into.put(field, value);
    }

    // ── List ────────────────────────────────────────────────────────────────

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

    // ── Delete ──────────────────────────────────────────────────────────────

    /** Deletes a backup and its per-collection child documents. */
    public void deleteBackup(String backupId, @NonNull Callback cb) {
        String uid = uid();
        if (uid == null) { cb.onError("Not signed in"); return; }

        DocumentReference backupRef = backupsRef(uid).document(backupId);
        backupRef.collection("data").get().addOnSuccessListener(dataSnap -> {
            WriteBatch batch = db.batch();
            for (DocumentSnapshot d : dataSnap.getDocuments()) batch.delete(d.getReference());
            batch.delete(backupRef);
            batch.commit()
                    .addOnSuccessListener(v -> cb.onSuccess())
                    .addOnFailureListener(e -> cb.onError(e.getMessage()));
        }).addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    // ── Restore ─────────────────────────────────────────────────────────────

    /**
     * Restores a backup, replacing the live financial data.
     *
     * <p>An automatic safety snapshot is taken first, so a failure partway through
     * (or an unwanted restore) can be undone. A collection absent from the backup —
     * because the backup predates that feature — is <b>skipped, not wiped</b>: the
     * live data for it is left intact rather than cleared to match an empty backup.
     */
    public void restoreBackup(String backupId, @NonNull Callback cb) {
        String uid = uid();
        if (uid == null) { cb.onError("Not signed in"); return; }

        // Safety snapshot before we delete anything.
        createBackupInternal(uid, "Auto-backup (before restore)", true, new Callback() {
            @Override
            public void onSuccess() {
                doRestore(uid, backupId, cb);
            }

            @Override
            public void onError(String message) {
                // If we can't secure a rollback point, don't proceed to delete data.
                cb.onError("Couldn't create a safety backup, so restore was cancelled: " + message);
            }
        });
    }

    private void doRestore(String uid, String backupId, @NonNull Callback cb) {
        DocumentReference userRef = db.collection("users").document(uid);
        DocumentReference backupRef = backupsRef(uid).document(backupId);

        backupRef.get().addOnSuccessListener(metaDoc -> {
            if (!metaDoc.exists()) { cb.onError("Backup not found"); return; }

            backupRef.collection("data").get().addOnSuccessListener(dataSnap -> {
                // Map collection name -> its stored items.
                Map<String, List<Map<String, Object>>> byCollection = new HashMap<>();
                for (DocumentSnapshot d : dataSnap.getDocuments()) {
                    Object raw = d.get("items");
                    if (raw instanceof List) {
                        //noinspection unchecked
                        byCollection.put(d.getId(), (List<Map<String, Object>>) raw);
                    }
                }
                restoreCollections(uid, userRef, metaDoc, byCollection, cb);
            }).addOnFailureListener(e -> cb.onError(e.getMessage()));
        }).addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    private void restoreCollections(String uid, DocumentReference userRef, DocumentSnapshot metaDoc,
                                    Map<String, List<Map<String, Object>>> byCollection,
                                    @NonNull Callback cb) {
        // Fetch current docs for each collection PRESENT in the backup, so we can
        // delete-then-write. Collections missing from the backup are left alone.
        List<String> collectionsToRestore = new ArrayList<>();
        List<Task<?>> currentFetches = new ArrayList<>();
        for (String collection : BACKED_UP_COLLECTIONS) {
            if (!byCollection.containsKey(collection)) continue; // guard: absent = skip, don't wipe
            collectionsToRestore.add(collection);
            currentFetches.add(userRef.collection(collection).get());
        }

        Tasks.whenAllComplete(currentFetches).addOnCompleteListener(t -> {
            try {
                List<Object> deleteThenWrite = new ArrayList<>();
                for (int i = 0; i < collectionsToRestore.size(); i++) {
                    String collection = collectionsToRestore.get(i);
                    com.google.firebase.firestore.QuerySnapshot current =
                            (com.google.firebase.firestore.QuerySnapshot) currentFetches.get(i).getResult();

                    // Delete every current doc, then write the backed-up ones.
                    if (current != null) {
                        for (DocumentSnapshot d : current.getDocuments()) {
                            deleteThenWrite.add(d.getReference()); // marker: delete
                        }
                    }
                    CollectionReference ref = userRef.collection(collection);
                    for (Map<String, Object> item : byCollection.get(collection)) {
                        Object id = item.get("id");
                        DocumentReference target = (id instanceof String && !((String) id).isEmpty())
                                ? ref.document((String) id) : ref.document();
                        deleteThenWrite.add(new WriteOp(target, item)); // marker: set
                    }
                }

                // Restore money-rule config from the backup metadata, when present.
                Map<String, Object> ruleUpdate = new HashMap<>();
                copyIfPresent(metaDoc, ruleUpdate, FIELD_MONTHLY_INCOME);
                copyIfPresent(metaDoc, ruleUpdate, FIELD_RULE_NEEDS);
                copyIfPresent(metaDoc, ruleUpdate, FIELD_RULE_WANTS);
                copyIfPresent(metaDoc, ruleUpdate, FIELD_RULE_INVESTMENTS);

                commitRestore(userRef, deleteThenWrite, ruleUpdate, cb);
            } catch (RuntimeException e) {
                cb.onError(e.getMessage());
            }
        });
    }

    /** A pending {@code set} operation for the restore batch. */
    private static final class WriteOp {
        final DocumentReference ref;
        final Map<String, Object> data;

        WriteOp(DocumentReference ref, Map<String, Object> data) {
            this.ref = ref;
            this.data = data;
        }
    }

    private void commitRestore(DocumentReference userRef, List<Object> ops,
                               Map<String, Object> ruleUpdate, @NonNull Callback cb) {
        List<WriteBatch> batches = new ArrayList<>();
        WriteBatch batch = db.batch();
        int count = 0;

        for (Object op : ops) {
            if (op instanceof DocumentReference) {
                batch.delete((DocumentReference) op);
            } else if (op instanceof WriteOp) {
                WriteOp write = (WriteOp) op;
                batch.set(write.ref, write.data);
            }
            if (++count >= BATCH_LIMIT) { batches.add(batch); batch = db.batch(); count = 0; }
        }

        if (!ruleUpdate.isEmpty()) {
            batch.set(userRef, ruleUpdate, com.google.firebase.firestore.SetOptions.merge());
            count++;
        }
        if (count > 0) batches.add(batch);

        commitSequentially(batches, 0, cb);
    }

    private void commitSequentially(List<WriteBatch> batches, int index, @NonNull Callback cb) {
        if (index >= batches.size()) { cb.onSuccess(); return; }
        batches.get(index).commit()
                .addOnSuccessListener(v -> commitSequentially(batches, index + 1, cb))
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }
}
