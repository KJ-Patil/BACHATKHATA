package com.example.bachatkhata;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class CustomerLedgerViewModel extends ViewModel {

    private final MutableLiveData<List<Customer>> customers = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Double> totalToReceive = new MutableLiveData<>(0.0);
    private final MutableLiveData<Double> totalToPay = new MutableLiveData<>(0.0);

    private final FirebaseFirestore mFirestore = FirebaseFirestore.getInstance();
    private ListenerRegistration listenerRegistration;

    public LiveData<List<Customer>> getCustomers() {
        return customers;
    }

    public LiveData<Double> getTotalToReceive() {
        return totalToReceive;
    }

    public LiveData<Double> getTotalToPay() {
        return totalToPay;
    }

    public void observeCustomers(String uid) {
        if (listenerRegistration != null) {
            listenerRegistration.remove();
        }

        listenerRegistration = mFirestore.collection("users").document(uid).collection("customers")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;

                    List<Customer> customerList = new ArrayList<>();
                    double toReceive = 0;
                    double toPay = 0;

                    if (value != null) {
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            Customer c = Customer.fromDocument(doc);
                            customerList.add(c);

                            double balance = c.getBalance();
                            if (balance > 0) {
                                toReceive += balance;
                            } else if (balance < 0) {
                                toPay += Math.abs(balance);
                            }
                        }
                    }

                    customers.setValue(customerList);
                    totalToReceive.setValue(toReceive);
                    totalToPay.setValue(toPay);
                });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (listenerRegistration != null) {
            listenerRegistration.remove();
        }
    }
}
