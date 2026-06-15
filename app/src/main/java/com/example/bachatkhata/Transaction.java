package com.example.bachatkhata;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.PropertyName;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class Transaction implements Serializable {

    private String id;
    private double amount;
    private String type; // "income" or "expense"
    private String category;
    private String note;
    private Date date;
    private String account; // "Cash", "UPI", "Bank Card", "Net Banking"
    private String currency;
    private String currencySymbol;
    private String source = "manual"; // "manual", "sms", "voice", "ocr"
    private String receiptUrl; // optional Storage path
    private Timestamp createdAt;

    public Transaction() {
        // Required empty public constructor for Firestore
    }

    public Transaction(String id, double amount, String type, String category, String note, Date date, 
                       String account, String currency, String currencySymbol, String source, 
                       String receiptUrl, Timestamp createdAt) {
        this.id = id;
        this.amount = amount;
        this.type = type;
        this.category = category;
        this.note = note;
        this.date = date;
        this.account = account;
        this.currency = currency;
        this.currencySymbol = currencySymbol;
        this.source = source != null ? source : "manual";
        this.receiptUrl = receiptUrl;
        this.createdAt = createdAt;
    }

    public Transaction(String id, double amount, String type, String category, String note, Date date, 
                       String account, String currency, String currencySymbol, Timestamp createdAt) {
        this(id, amount, type, category, note, date, account, currency, currencySymbol, "manual", null, createdAt);
    }

    @PropertyName("id")
    public String getId() {
        return id;
    }

    @PropertyName("id")
    public void setId(String id) {
        this.id = id;
    }

    @PropertyName("amount")
    public double getAmount() {
        return amount;
    }

    @PropertyName("amount")
    public void setAmount(double amount) {
        this.amount = amount;
    }

    @PropertyName("type")
    public String getType() {
        return type;
    }

    @PropertyName("type")
    public void setType(String type) {
        this.type = type;
    }

    @PropertyName("category")
    public String getCategory() {
        return category;
    }

    @PropertyName("category")
    public void setCategory(String category) {
        this.category = category;
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
    public Date getDate() {
        return date;
    }

    @PropertyName("date")
    public void setDate(Date date) {
        this.date = date;
    }

    @PropertyName("account")
    public String getAccount() {
        return account;
    }

    @PropertyName("account")
    public void setAccount(String account) {
        this.account = account;
    }

    @PropertyName("currency")
    public String getCurrency() {
        return currency;
    }

    @PropertyName("currency")
    public void setCurrency(String currency) {
        this.currency = currency;
    }

    @PropertyName("currencySymbol")
    public String getCurrencySymbol() {
        return currencySymbol;
    }

    @PropertyName("currencySymbol")
    public void setCurrencySymbol(String currencySymbol) {
        this.currencySymbol = currencySymbol;
    }

    @PropertyName("source")
    public String getSource() {
        return source;
    }

    @PropertyName("source")
    public void setSource(String source) {
        this.source = source != null ? source : "manual";
    }

    @PropertyName("receiptUrl")
    public String getReceiptUrl() {
        return receiptUrl;
    }

    @PropertyName("receiptUrl")
    public void setReceiptUrl(String receiptUrl) {
        this.receiptUrl = receiptUrl;
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
        map.put("amount", amount);
        map.put("type", type);
        map.put("category", category);
        map.put("note", note);
        map.put("date", date != null ? new Timestamp(date) : null);
        map.put("account", account);
        map.put("currency", currency);
        map.put("currencySymbol", currencySymbol);
        map.put("source", source);
        map.put("receiptUrl", receiptUrl);
        map.put("createdAt", createdAt);
        return map;
    }

    public static Transaction fromDocument(DocumentSnapshot doc) {
        Transaction t = new Transaction();
        t.setId(doc.getString("id"));
        Double amt = doc.getDouble("amount");
        t.setAmount(amt != null ? amt : 0.0);
        t.setType(doc.getString("type"));
        t.setCategory(doc.getString("category"));
        t.setNote(doc.getString("note"));
        
        Timestamp dateTimestamp = doc.getTimestamp("date");
        t.setDate(dateTimestamp != null ? dateTimestamp.toDate() : null);
        
        t.setAccount(doc.getString("account"));
        t.setCurrency(doc.getString("currency"));
        t.setCurrencySymbol(doc.getString("currencySymbol"));
        
        String src = doc.getString("source");
        t.setSource(src != null ? src : "manual");
        
        t.setReceiptUrl(doc.getString("receiptUrl"));
        t.setCreatedAt(doc.getTimestamp("createdAt"));
        return t;
    }
}
