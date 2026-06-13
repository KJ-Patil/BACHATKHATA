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

    public String formatAmount(double amount) {
        try {
            NumberFormat formatter;
            if ("INR".equals(currentCurrencyCode)) {
                // Indian formatting grouping (e.g. ₹1,23,456.00)
                formatter = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
            } else {
                formatter = NumberFormat.getCurrencyInstance(Locale.US);
            }
            String formatted = formatter.format(amount);
            // Replace local currency symbols with current currency symbol configured
            // (e.g. formatting can produce Rs. or USD, we replace with current symbol for cleanliness)
            String symbolToReplace = formatter.getCurrency().getSymbol(new Locale("en", "IN"));
            if ("INR".equals(currentCurrencyCode)) {
                return formatted.replace(symbolToReplace, currentCurrencySymbol).trim();
            }
            return currentCurrencySymbol + String.format(Locale.US, "%,.2f", amount);
        } catch (Exception e) {
            return currentCurrencySymbol + String.format(Locale.US, "%.2f", amount);
        }
    }

    public String getSymbol(String code) {
        return supportedCurrencies.getOrDefault(code, "₹");
    }
}
