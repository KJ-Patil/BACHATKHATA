package com.example.bachatkhata;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DebtSimplifier {

    public static class Settlement implements Serializable {
        public String from;
        public String to;
        public double amount;

        public Settlement() {
            // Required empty constructor
        }

        public Settlement(String from, String to, double amount) {
            this.from = from;
            this.to = to;
            this.amount = amount;
        }
    }

    public static List<Settlement> simplify(Map<String, Double> balances) {
        List<Settlement> settlements = new ArrayList<>();
        Map<String, Double> activeBalances = new HashMap<>();

        // Clean values, ignore values that are extremely close to zero
        for (Map.Entry<String, Double> entry : balances.entrySet()) {
            double val = entry.getValue();
            if (Math.abs(val) > 0.01) {
                activeBalances.put(entry.getKey(), val);
            }
        }

        while (true) {
            String maxDebtor = null;
            double minVal = 0.01; // Negative represents debt, find most negative
            
            String maxCreditor = null;
            double maxVal = 0.01; // Positive represents credit, find most positive

            for (Map.Entry<String, Double> entry : activeBalances.entrySet()) {
                double val = entry.getValue();
                if (val < minVal) {
                    minVal = val;
                    maxDebtor = entry.getKey();
                }
                if (val > maxVal) {
                    maxVal = val;
                    maxCreditor = entry.getKey();
                }
            }

            if (maxDebtor == null || maxCreditor == null) {
                break; // Settle complete
            }

            double debtVal = -minVal;
            double settleAmount = Math.min(debtVal, maxVal);

            if (settleAmount > 0.01) {
                settlements.add(new Settlement(maxDebtor, maxCreditor, settleAmount));
            }

            double newDebtorBal = activeBalances.get(maxDebtor) + settleAmount;
            double newCreditorBal = activeBalances.get(maxCreditor) - settleAmount;

            if (Math.abs(newDebtorBal) <= 0.01) {
                activeBalances.remove(maxDebtor);
            } else {
                activeBalances.put(maxDebtor, newDebtorBal);
            }

            if (Math.abs(newCreditorBal) <= 0.01) {
                activeBalances.remove(maxCreditor);
            } else {
                activeBalances.put(maxCreditor, newCreditorBal);
            }
        }

        return settlements;
    }
}
