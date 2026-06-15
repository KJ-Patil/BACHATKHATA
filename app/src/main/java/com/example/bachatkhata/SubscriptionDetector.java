package com.example.bachatkhata;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SubscriptionDetector {

    public static void detectFromTransactions(String uid, List<Transaction> transactions) {
        if (transactions == null || transactions.isEmpty()) return;

        // Group expense transactions by merchant (note/merchant) and amount
        Map<String, List<Transaction>> groups = new HashMap<>();

        for (Transaction t : transactions) {
            if (!"expense".equalsIgnoreCase(t.getType())) continue;
            
            // Clean merchant name from note
            String merchant = cleanMerchantName(t.getNote(), t.getCategory());
            double amount = t.getAmount();
            
            // Key is: merchant_name + "#" + amount
            String key = merchant.toLowerCase().trim() + "#" + amount;
            
            if (!groups.containsKey(key)) {
                groups.put(key, new ArrayList<>());
            }
            groups.get(key).add(t);
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        for (Map.Entry<String, List<Transaction>> entry : groups.entrySet()) {
            List<Transaction> groupTxns = entry.getValue();
            if (groupTxns.size() < 2) continue;

            // Sort transactions oldest to newest
            Collections.sort(groupTxns, (t1, t2) -> t1.getDate().compareTo(t2.getDate()));

            // Check average gap in days
            List<Long> gaps = new ArrayList<>();
            for (int i = 1; i < groupTxns.size(); i++) {
                long diffMs = groupTxns.get(i).getDate().getTime() - groupTxns.get(i - 1).getDate().getTime();
                long diffDays = TimeUnit.MILLISECONDS.toDays(diffMs);
                gaps.add(diffDays);
            }

            double sumGaps = 0;
            boolean allGapsWithinRange = true;
            for (long gap : gaps) {
                sumGaps += gap;
                if (gap < 25 || gap > 35) {
                    allGapsWithinRange = false;
                }
            }
            double avgGap = sumGaps / gaps.size();

            // If average gap is roughly monthly (25-35 days)
            if (avgGap >= 25 && avgGap <= 35 && allGapsWithinRange) {
                // Detected as a subscription!
                Transaction latest = groupTxns.get(groupTxns.size() - 1);
                String merchantName = cleanMerchantName(latest.getNote(), latest.getCategory());
                double amount = latest.getAmount();

                // Calculate next renewal date = latest txn date + 30 days
                Calendar cal = Calendar.getInstance();
                cal.setTime(latest.getDate());
                cal.add(Calendar.DAY_OF_YEAR, 30);
                Date nextRenewal = cal.getTime();

                // If next renewal is in the past, roll it forward by 30 days until it's in the future
                Date now = new Date();
                while (nextRenewal.before(now)) {
                    cal.add(Calendar.DAY_OF_YEAR, 30);
                    nextRenewal = cal.getTime();
                }

                // Write/Update subscription in Firestore
                String cleanId = merchantName.toLowerCase().replaceAll("[^a-z0-9]", "_") + "_" + ((int) amount);
                Map<String, Object> sub = new HashMap<>();
                sub.put("id", cleanId);
                sub.put("name", merchantName);
                sub.put("amount", amount);
                sub.put("frequency", "Monthly");
                sub.put("nextRenewalDate", nextRenewal);
                sub.put("isActive", true);
                sub.put("isAutoDetected", true);

                db.collection("users").document(uid).collection("subscriptions")
                        .document(cleanId)
                        .set(sub);
            }
        }
    }

    private static String cleanMerchantName(String note, String category) {
        if (note == null || note.trim().isEmpty()) {
            return category != null ? category : "Subscription";
        }
        String clean = note.replace("(Auto-Imported)", "").trim();
        // Capitalize words
        String[] words = clean.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            sb.append(Character.toUpperCase(w.charAt(0)))
              .append(w.substring(1).toLowerCase())
              .append(" ");
        }
        return sb.toString().trim();
    }
}
