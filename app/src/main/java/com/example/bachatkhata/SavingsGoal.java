package com.example.bachatkhata;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.PropertyName;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class SavingsGoal implements Serializable {

    private String id;
    private String name;
    private String icon; // emoji
    private double targetAmount;
    private double savedAmount;
    private String currency;
    private Timestamp deadline;
    private Timestamp createdAt;

    public SavingsGoal() {
        // Required empty public constructor for Firestore
    }

    public SavingsGoal(String id, String name, String icon, double targetAmount, double savedAmount, String currency, Timestamp deadline, Timestamp createdAt) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.targetAmount = targetAmount;
        this.savedAmount = savedAmount;
        this.currency = currency;
        this.deadline = deadline;
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

    @PropertyName("icon")
    public String getIcon() {
        return icon;
    }

    @PropertyName("icon")
    public void setIcon(String icon) {
        this.icon = icon;
    }

    @PropertyName("targetAmount")
    public double getTargetAmount() {
        return targetAmount;
    }

    @PropertyName("targetAmount")
    public void setTargetAmount(double targetAmount) {
        this.targetAmount = targetAmount;
    }

    @PropertyName("savedAmount")
    public double getSavedAmount() {
        return savedAmount;
    }

    @PropertyName("savedAmount")
    public void setSavedAmount(double savedAmount) {
        this.savedAmount = savedAmount;
    }

    @PropertyName("currency")
    public String getCurrency() {
        return currency;
    }

    @PropertyName("currency")
    public void setCurrency(String currency) {
        this.currency = currency;
    }

    @PropertyName("deadline")
    public Timestamp getDeadline() {
        return deadline;
    }

    @PropertyName("deadline")
    public void setDeadline(Timestamp deadline) {
        this.deadline = deadline;
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
        map.put("icon", icon);
        map.put("targetAmount", targetAmount);
        map.put("savedAmount", savedAmount);
        map.put("currency", currency);
        map.put("deadline", deadline);
        map.put("createdAt", createdAt);
        return map;
    }

    public static SavingsGoal fromDocument(DocumentSnapshot doc) {
        SavingsGoal g = new SavingsGoal();
        g.setId(doc.getString("id"));
        g.setName(doc.getString("name"));
        g.setIcon(doc.getString("icon"));
        
        Double target = doc.getDouble("targetAmount");
        g.setTargetAmount(target != null ? target : 0.0);
        
        Double saved = doc.getDouble("savedAmount");
        g.setSavedAmount(saved != null ? saved : 0.0);
        
        g.setCurrency(doc.getString("currency"));
        g.setDeadline(doc.getTimestamp("deadline"));
        g.setCreatedAt(doc.getTimestamp("createdAt"));
        return g;
    }
}
