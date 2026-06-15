package com.example.bachatkhata;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.PropertyName;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Customer implements Serializable {

    private String id;
    private String name;
    private String phone;
    private String type; // "customer" (you will receive / you gave) or "supplier" (you will pay / you got)
    private double balance; // positive = owed to you, negative = you owe them
    private Timestamp createdAt;

    public Customer() {
        // Required for Firestore serialization
    }

    public Customer(String id, String name, String phone, String type, double balance, Timestamp createdAt) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.type = type;
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

    @PropertyName("type")
    public String getType() {
        return type;
    }

    @PropertyName("type")
    public void setType(String type) {
        this.type = type;
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
        map.put("type", type);
        map.put("balance", balance);
        map.put("createdAt", createdAt);
        return map;
    }

    public static Customer fromDocument(DocumentSnapshot doc) {
        Customer c = new Customer();
        c.setId(doc.getString("id"));
        c.setName(doc.getString("name"));
        c.setPhone(doc.getString("phone"));
        c.setType(doc.getString("type"));
        Double bal = doc.getDouble("balance");
        c.setBalance(bal != null ? bal : 0.0);
        c.setCreatedAt(doc.getTimestamp("createdAt"));
        return c;
    }
}
