package com.example.bachatkhata;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.PropertyName;

import java.io.Serializable;

public class Budget implements Serializable {

    private String id;
    private String category;
    private double limitAmount;
    private String period; // "monthly"
    private int month; // 0-indexed: 0=Jan, 11=Dec
    private int year;

    public Budget() {
        // Required empty public constructor for Firestore
    }

    public Budget(String id, String category, double limitAmount, String period, int month, int year) {
        this.id = id;
        this.category = category;
        this.limitAmount = limitAmount;
        this.period = period;
        this.month = month;
        this.year = year;
    }

    @PropertyName("id")
    public String getId() {
        return id;
    }

    @PropertyName("id")
    public void setId(String id) {
        this.id = id;
    }

    @PropertyName("category")
    public String getCategory() {
        return category;
    }

    @PropertyName("category")
    public void setCategory(String category) {
        this.category = category;
    }

    @PropertyName("limitAmount")
    public double getLimitAmount() {
        return limitAmount;
    }

    @PropertyName("limitAmount")
    public void setLimitAmount(double limitAmount) {
        this.limitAmount = limitAmount;
    }

    @PropertyName("period")
    public String getPeriod() {
        return period;
    }

    @PropertyName("period")
    public void setPeriod(String period) {
        this.period = period;
    }

    @PropertyName("month")
    public int getMonth() {
        return month;
    }

    @PropertyName("month")
    public void setMonth(int month) {
        this.month = month;
    }

    @PropertyName("year")
    public int getYear() {
        return year;
    }

    @PropertyName("year")
    public void setYear(int year) {
        this.year = year;
    }

    public static Budget fromDocument(DocumentSnapshot doc) {
        Budget b = new Budget();
        b.setId(doc.getString("id"));
        b.setCategory(doc.getString("category"));
        Double limit = doc.getDouble("limitAmount");
        b.setLimitAmount(limit != null ? limit : 0.0);
        b.setPeriod(doc.getString("period"));
        
        Long m = doc.getLong("month");
        b.setMonth(m != null ? m.intValue() : 0);
        
        Long y = doc.getLong("year");
        b.setYear(y != null ? y.intValue() : 2026);
        return b;
    }
}
