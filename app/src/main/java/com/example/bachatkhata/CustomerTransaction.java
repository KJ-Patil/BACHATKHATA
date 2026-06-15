package com.example.bachatkhata;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.PropertyName;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class CustomerTransaction implements Serializable {

    private String id;
    private String customerId;
    private double amount;
    private String note;
    private Timestamp date;
    private String type; // "gave" (you gave credit / cash to customer) or "got" (you got payment / cash from customer)

    public CustomerTransaction() {
        // Required for Firestore serialization
    }

    public CustomerTransaction(String id, String customerId, double amount, String note, Timestamp date, String type) {
        this.id = id;
        this.customerId = customerId;
        this.amount = amount;
        this.note = note;
        this.date = date;
        this.type = type;
    }

    @PropertyName("id")
    public String getId() {
        return id;
    }

    @PropertyName("id")
    public void setId(String id) {
        this.id = id;
    }

    @PropertyName("customerId")
    public String getCustomerId() {
        return customerId;
    }

    @PropertyName("customerId")
    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    @PropertyName("amount")
    public double getAmount() {
        return amount;
    }

    @PropertyName("amount")
    public void setAmount(double amount) {
        this.amount = amount;
    }

    @PropertyName("note")
    public String getNote() {
        return note;
    }

    @PropertyName("note")
    public void setNote(String note) {
        this.note = note;
    }

    @PropertyName("date")
    public Timestamp getDate() {
        return date;
    }

    @PropertyName("date")
    public void setDate(Timestamp date) {
        this.date = date;
    }

    @PropertyName("type")
    public String getType() {
        return type;
    }

    @PropertyName("type")
    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("customerId", customerId);
        map.put("amount", amount);
        map.put("note", note);
        map.put("date", date);
        map.put("type", type);
        return map;
    }

    public static CustomerTransaction fromDocument(DocumentSnapshot doc) {
        CustomerTransaction t = new CustomerTransaction();
        t.setId(doc.getString("id"));
        t.setCustomerId(doc.getString("customerId"));
        Double amt = doc.getDouble("amount");
        t.setAmount(amt != null ? amt : 0.0);
        t.setNote(doc.getString("note"));
        t.setDate(doc.getTimestamp("date"));
        t.setType(doc.getString("type"));
        return t;
    }
}
