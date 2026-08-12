package com.example.bachatkhata;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.PropertyName;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * One account book in the Notebook Ledger.
 *
 * <p>There is deliberately <b>no</b> "customer" / "supplier" field. The sign of the
 * balance already says which way the book runs, and a fixed label went stale the
 * moment a customer's balance crossed zero — a contact tagged "customer" while
 * showing "YOU WILL GIVE" is worse than no label. The add-account form asks for the
 * <em>direction</em> of the opening balance instead and folds it into the sign.
 *
 * <p>Documents written by older builds may still carry a {@code type} field; it is
 * simply ignored, and their balances are already correct as stored.
 */
public class Customer implements Serializable {

    private String id;
    private String name;
    private String phone;
    /** Positive: they owe us (credit) · negative: we owe them (debit). */
    private double balance;
    private Timestamp createdAt;

    public Customer() {
        // Required for Firestore serialization
    }

    public Customer(String id, String name, String phone, double balance, Timestamp createdAt) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.balance = balance;
        this.createdAt = createdAt;
    }

    @PropertyName("id")
    public String getId() {
        return id;
    }

    @PropertyName("id")
    public void setId(String id) {
        this.id = id;
    }

    @PropertyName("name")
    public String getName() {
        return name;
    }

    @PropertyName("name")
    public void setName(String name) {
        this.name = name;
    }

    @PropertyName("phone")
    public String getPhone() {
        return phone;
    }

    @PropertyName("phone")
    public void setPhone(String phone) {
        this.phone = phone;
    }

    @PropertyName("balance")
    public double getBalance() {
        return balance;
    }

    @PropertyName("balance")
    public void setBalance(double balance) {
        this.balance = balance;
    }

    @PropertyName("createdAt")
    public Timestamp getCreatedAt() {
        return createdAt;
    }

    @PropertyName("createdAt")
    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("phone", phone);
        map.put("balance", balance);
        map.put("createdAt", createdAt);
        return map;
    }

    public static Customer fromDocument(DocumentSnapshot doc) {
        Customer c = new Customer();
        c.setId(doc.getString("id"));
        c.setName(doc.getString("name"));
        c.setPhone(doc.getString("phone"));
        Double bal = doc.getDouble("balance");
        c.setBalance(bal != null ? bal : 0.0);
        c.setCreatedAt(doc.getTimestamp("createdAt"));
        return c;
    }
}
