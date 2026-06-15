package com.example.bachatkhata;

import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CurrencyManager {

    private static CurrencyManager instance;
    private String currentCurrencyCode = "INR";
    private String currentCurrencySymbol = "₹";
    private final Map<String, String> supportedCurrencies = new HashMap<>();

    private CurrencyManager() {
        // Initialize 20 supported currencies
        supportedCurrencies.put("INR", "₹");
        supportedCurrencies.put("USD", "$");
        supportedCurrencies.put("EUR", "€");
        supportedCurrencies.put("GBP", "£");
        supportedCurrencies.put("JPY", "¥");
        supportedCurrencies.put("AUD", "A$");
        supportedCurrencies.put("CAD", "C$");
        supportedCurrencies.put("CHF", "Fr");
        supportedCurrencies.put("CNY", "¥");
        supportedCurrencies.put("SGD", "S$");
        supportedCurrencies.put("AED", "د.إ");
        supportedCurrencies.put("SAR", "﷼");
        supportedCurrencies.put("KWD", "د.ك");
        supportedCurrencies.put("QAR", "﷼");
        supportedCurrencies.put("MYR", "RM");
        supportedCurrencies.put("THB", "฿");
        supportedCurrencies.put("IDR", "Rp");
        supportedCurrencies.put("PHP", "₱");
        supportedCurrencies.put("BRL", "R$");
        supportedCurrencies.put("ZAR", "R");
    }

    public static synchronized CurrencyManager getInstance() {
        if (instance == null) {
            instance = new CurrencyManager();
        }
        return instance;
    }

    public String getCurrentCurrencyCode() {
        return currentCurrencyCode;
    }

    public String getCurrentCurrencySymbol() {
        return currentCurrencySymbol;
    }

    public Map<String, String> getSupportedCurrencies() {
        return supportedCurrencies;
    }

    // Overloaded loadFromFirestore for compatibility
    public void loadFromFirestore(String uid) {
        loadFromFirestore(uid, null);
    }

    public void loadFromFirestore(String uid, Runnable onLoaded) {
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String code = documentSnapshot.getString("currency");
                        String symbol = documentSnapshot.getString("currencySymbol");
                        if (code != null) currentCurrencyCode = code;
                        if (symbol != null) currentCurrencySymbol = symbol;
                    }
                    if (onLoaded != null) onLoaded.run();
                })
                .addOnFailureListener(e -> {
                    if (onLoaded != null) onLoaded.run();
                });
    }

    // Overloaded saveToFirestore for compatibility
    public void saveToFirestore(String uid, String code, String symbol) {
        saveToFirestore(uid, code, symbol, null);
    }

    public void saveToFirestore(String uid, String code, String symbol, Runnable onSaved) {
        currentCurrencyCode = code;
        currentCurrencySymbol = symbol;
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("currency", code);
        updates.put("currencySymbol", symbol);

        FirebaseFirestore.getInstance().collection("users").document(uid)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    if (onSaved != null) onSaved.run();
                })
                .addOnFailureListener(e -> {
                    if (onSaved != null) onSaved.run();
                });
    }

    // Indian number format for INR (e.g. 1,23,456.00), standard for others
    public String formatAmount(double amount) {
        if ("INR".equals(currentCurrencyCode)) {
            return currentCurrencySymbol + formatIndian(amount);
        } else {
            return currentCurrencySymbol + String.format(Locale.US, "%,.2f", amount);
        }
    }

    private String formatIndian(double amount) {
        boolean negative = amount < 0;
        amount = Math.abs(amount);
        String amountStr = String.format(Locale.US, "%.2f", amount);
        int dotIndex = amountStr.indexOf('.');
        String integerPart = amountStr.substring(0, dotIndex);
        String decimalPart = amountStr.substring(dotIndex);
        
        int len = integerPart.length();
        if (len <= 3) {
            return (negative ? "-" : "") + integerPart + decimalPart;
        }
        
        String lastThree = integerPart.substring(len - 3);
        String remaining = integerPart.substring(0, len - 3);
        
        StringBuilder builder = new StringBuilder();
        int remLen = remaining.length();
        for (int i = remLen - 1; i >= 0; i--) {
            builder.append(remaining.charAt(i));
            int posFromRight = remLen - i;
            if (posFromRight % 2 == 0 && i > 0) {
                builder.append(',');
            }
        }
        String groupedRemaining = builder.reverse().toString();
        return (negative ? "-" : "") + groupedRemaining + "," + lastThree + decimalPart;
    }

    public String getSymbol(String code) {
        return supportedCurrencies.getOrDefault(code, "₹");
    }
}
