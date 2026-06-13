package com.example.bachatkhata;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class TransactionRepository {

    private static TransactionRepository instance;
    private final FirebaseFirestore mFirestore;
    private ListenerRegistration transactionsListener;

    private TransactionRepository() {
        mFirestore = FirebaseFirestore.getInstance();
    }

    public static synchronized TransactionRepository getInstance() {
        if (instance == null) {
            instance = new TransactionRepository();
        }
        return instance;
    }

    public void observeTransactions(String uid, OnTransactionLoadedListener callback) {
        if (transactionsListener != null) {
            transactionsListener.remove();
        }

        CollectionReference transactionsCol = mFirestore.collection("users")
                .document(uid)
                .collection("transactions");

        transactionsListener = transactionsCol.orderBy("date", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        return;
                    }
                    List<Transaction> transactions = new ArrayList<>();
                    if (value != null) {
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            Transaction t = Transaction.fromDocument(doc);
                            transactions.add(t);
                        }
                    }
                    callback.onLoaded(transactions);
                });
    }

    public void addTransaction(String uid, Transaction t, OnSuccessListener<Void> success, OnFailureListener failure) {
        String docId = mFirestore.collection("users").document(uid).collection("transactions").document().getId();
        t.setId(docId);
        mFirestore.collection("users").document(uid).collection("transactions").document(docId)
                .set(t.toMap())
                .addOnSuccessListener(success)
                .addOnFailureListener(failure);
    }

    public void updateTransaction(String uid, Transaction t, OnSuccessListener<Void> success, OnFailureListener failure) {
        mFirestore.collection("users").document(uid).collection("transactions").document(t.getId())
                .set(t.toMap())
                .addOnSuccessListener(success)
                .addOnFailureListener(failure);
    }

    public void deleteTransaction(String uid, String txnId, OnSuccessListener<Void> success, OnFailureListener failure) {
        mFirestore.collection("users").document(uid).collection("transactions").document(txnId)
                .delete()
                .addOnSuccessListener(success)
                .addOnFailureListener(failure);
    }

    public void removeListener() {
        if (transactionsListener != null) {
            transactionsListener.remove();
            transactionsListener = null;
        }
    }

    public interface OnTransactionLoadedListener {
        void onLoaded(List<Transaction> list);
    }
}
