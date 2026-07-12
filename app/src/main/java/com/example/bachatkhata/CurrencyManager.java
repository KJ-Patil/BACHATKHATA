package com.example.bachatkhata;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

public class CurrencyManager {

    // Base currency for all stored rates. Every rate is "units of X per 1 INR".
    private static final String BASE_CURRENCY = "INR";
    // Free, no-key live FX endpoint (https://www.exchangerate-api.com/docs/free).
    private static final String RATES_ENDPOINT = "https://open.er-api.com/v6/latest/" + BASE_CURRENCY;
    private static final long RATES_TTL_MS = 12 * 60 * 60 * 1000L; // 12 hours

    private static final String PREF_NAME = "BachatKhata_Rates";
    private static final String KEY_RATES_JSON = "rates_json";
    private static final String KEY_RATES_FETCHED_AT = "rates_fetched_at";

    private static CurrencyManager instance;
    private String currentCurrencyCode = "INR";
    private String currentCurrencySymbol = "₹";
    private final Map<String, String> supportedCurrencies = new HashMap<>();
    private final Map<String, Double> exchangeRates = new HashMap<>();
    private SharedPreferences ratesPrefs;

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

    /**
     * Wire up a persistent cache for exchange rates. Call once from Application.onCreate().
     * Loads any cached rates immediately and refreshes them in the background if stale.
     */
    public void init(Context context) {
        if (ratesPrefs == null) {
            ratesPrefs = context.getApplicationContext()
                    .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            loadCachedRates();
        }
        refreshExchangeRates(false);
    }

    private void loadCachedRates() {
        if (ratesPrefs == null) return;
        String json = ratesPrefs.getString(KEY_RATES_JSON, null);
        if (json == null) return;
        try {
            JSONObject obj = new JSONObject(json);
            Iterator<String> keys = obj.keys();
            synchronized (exchangeRates) {
                while (keys.hasNext()) {
                    String code = keys.next();
                    exchangeRates.put(code, obj.getDouble(code));
                }
            }
        } catch (Exception ignored) {
            // Corrupt cache — will be replaced on next successful refresh.
        }
    }

    /**
     * Fetch live INR-based exchange rates. Runs on a background thread and is safe to call
     * from the UI thread. Honours a 12h cache unless {@code force} is true. Fails silently
     * (keeps last-known rates) when offline.
     */
    public void refreshExchangeRates(boolean force) {
        if (ratesPrefs == null) return; // init() not called yet
        long fetchedAt = ratesPrefs.getLong(KEY_RATES_FETCHED_AT, 0L);
        boolean fresh = System.currentTimeMillis() - fetchedAt < RATES_TTL_MS;
        if (!force && fresh && !exchangeRates.isEmpty()) return;

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(RATES_ENDPOINT);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestMethod("GET");
                if (conn.getResponseCode() != 200) return;

                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                }

                JSONObject root = new JSONObject(sb.toString());
                if (!"success".equals(root.optString("result"))) return;
                JSONObject rates = root.getJSONObject("rates");

                synchronized (exchangeRates) {
                    exchangeRates.clear();
                    Iterator<String> keys = rates.keys();
                    while (keys.hasNext()) {
                        String code = keys.next();
                        exchangeRates.put(code, rates.getDouble(code));
                    }
                }
                if (ratesPrefs != null) {
                    ratesPrefs.edit()
                            .putString(KEY_RATES_JSON, rates.toString())
                            .putLong(KEY_RATES_FETCHED_AT, System.currentTimeMillis())
                            .apply();
                }
            } catch (Exception ignored) {
                // Offline / parse error — retain last-known cached rates.
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    /** Units of {@code code} per 1 INR. Returns 1.0 for INR or when the rate is unknown. */
    public double getExchangeRate(String code) {
        if (BASE_CURRENCY.equals(code)) return 1.0;
        synchronized (exchangeRates) {
            Double rate = exchangeRates.get(code);
            return (rate != null && rate > 0) ? rate : 1.0;
        }
    }

    /** Convert an amount between any two supported currencies via the INR base. */
    public double convert(double amount, String fromCode, String toCode) {
        if (fromCode.equals(toCode)) return amount;
        double amountInInr = amount / getExchangeRate(fromCode);
        return amountInInr * getExchangeRate(toCode);
    }

    /** Convert an INR amount into the user's currently selected currency. */
    public double convertFromBase(double amountInInr) {
        return amountInInr * getExchangeRate(currentCurrencyCode);
    }

    /** True once at least one live/cached rate is available. */
    public boolean hasRates() {
        synchronized (exchangeRates) {
            return !exchangeRates.isEmpty();
        }
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

        // set(..., merge) creates the user doc if it doesn't exist yet, unlike
        // update() which fails with NOT_FOUND and would silently revert the change.
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .set(updates, SetOptions.merge())
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
