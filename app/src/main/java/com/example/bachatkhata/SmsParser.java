package com.example.bachatkhata;

import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsParser {

    public static class ParsedTransaction implements java.io.Serializable {
        public double amount;
        public String type; // credit | debit
        public String accountLast4;
        public String merchant;
        public Date date;

        public ParsedTransaction() {
            this.date = new Date();
            this.merchant = "Unknown Merchant";
            this.type = "debit";
            this.accountLast4 = "XXXX";
        }
    }

    public static ParsedTransaction parse(String smsBody) {
        if (smsBody == null || smsBody.trim().isEmpty()) {
            return null;
        }

        String bodyLower = smsBody.toLowerCase();
        ParsedTransaction txn = new ParsedTransaction();
        boolean matched = false;

        // 1. Regex Matchers for different banks
        // HDFC: Rs.1,200.00 debited/credited
        Pattern hdfcPattern = Pattern.compile("rs\\.([\\d,]+(?:\\.\\d{2})?) (?:debited|credited)", Pattern.CASE_INSENSITIVE);
        // SBI: INR 500.00 debited/credited
        Pattern sbiPattern = Pattern.compile("inr ([\\d,]+(?:\\.\\d{2})?) (?:debited|credited)", Pattern.CASE_INSENSITIVE);
        // ICICI: debited/credited with Rs.1,500.00
        Pattern iciciPattern = Pattern.compile("(?:debited|credited) with rs\\.([\\d,]+(?:\\.\\d{2})?)", Pattern.CASE_INSENSITIVE);
        // Axis: debited/credited INR 250 or Rs. 250
        Pattern axisPattern = Pattern.compile("(?:debited|credited) (?:inr|rs\\.?) ?([\\d,]+(?:\\.\\d{2})?)", Pattern.CASE_INSENSITIVE);
        // Kotak: debit/credit of INR 100
        Pattern kotakPattern = Pattern.compile("(?:debit|credit) of (?:inr|rs) ?([\\d,]+(?:\\.\\d{2})?)", Pattern.CASE_INSENSITIVE);
        // General UPI: paid/received INR 300
        Pattern upiPattern = Pattern.compile("(?:paid|received) (?:inr|rs\\.?) ?([\\d,]+(?:\\.\\d{2})?)", Pattern.CASE_INSENSITIVE);

        Matcher m;

        if ((m = hdfcPattern.matcher(smsBody)).find()) {
            txn.amount = cleanAmount(m.group(1));
            matched = true;
        } else if ((m = sbiPattern.matcher(smsBody)).find()) {
            txn.amount = cleanAmount(m.group(1));
            matched = true;
        } else if ((m = iciciPattern.matcher(smsBody)).find()) {
            txn.amount = cleanAmount(m.group(1));
            matched = true;
        } else if ((m = axisPattern.matcher(smsBody)).find()) {
            txn.amount = cleanAmount(m.group(1));
            matched = true;
        } else if ((m = kotakPattern.matcher(smsBody)).find()) {
            txn.amount = cleanAmount(m.group(1));
            matched = true;
        } else if ((m = upiPattern.matcher(smsBody)).find()) {
            txn.amount = cleanAmount(m.group(1));
            matched = true;
        }

        if (!matched) {
            return null;
        }

        // 2. Type Detection
        if (bodyLower.contains("credited") || bodyLower.contains("received") || bodyLower.contains("credit")) {
            txn.type = "income"; // credit maps to income in app context
        } else {
            txn.type = "expense"; // debit/paid maps to expense
        }

        // 3. Extract Account/Card Last 4 digits
        Pattern acctPattern = Pattern.compile("(?:a/c|acct|card|account|xxxx|xx)([0-9]{4})", Pattern.CASE_INSENSITIVE);
        Matcher acctMatcher = acctPattern.matcher(smsBody);
        if (acctMatcher.find()) {
            txn.accountLast4 = acctMatcher.group(1);
        }

        // 4. Extract Merchant Name
        Pattern merchantPattern = Pattern.compile("(?:at|to|from|vpa) ([a-zA-Z0-9\\.\\*\\-_]+(?: [a-zA-Z0-9\\.\\*\\-_]+){0,2})", Pattern.CASE_INSENSITIVE);
        Matcher merchantMatcher = merchantPattern.matcher(smsBody);
        if (merchantMatcher.find()) {
            String candidate = merchantMatcher.group(1).trim();
            // clean up trailing helper words like "on", "using", "ref"
            String[] stopWords = {"on", "using", "ref", "dt", "date", "via", "for", "balance", "bal", "avail"};
            for (String stop : stopWords) {
                if (candidate.toLowerCase().endsWith(" " + stop)) {
                    candidate = candidate.substring(0, candidate.length() - (stop.length() + 1)).trim();
                }
            }
            if (!candidate.isEmpty()) {
                txn.merchant = candidate;
            }
        }

        return txn;
    }

    private static double cleanAmount(String amtStr) {
        try {
            amtStr = amtStr.replace(",", "").trim();
            return Double.parseDouble(amtStr);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public static String mapMerchantToCategory(String merchant) {
        if (merchant == null) return "Other";
        String lower = merchant.toLowerCase();
        if (lower.contains("swiggy") || lower.contains("zomato") || lower.contains("food") || lower.contains("restaurant") || lower.contains("canteen") || lower.contains("dhaba")) {
            return "Food";
        }
        if (lower.contains("ola") || lower.contains("uber") || lower.contains("metro") || lower.contains("auto") || lower.contains("cab") || lower.contains("railway") || lower.contains("irctc")) {
            return "Transport";
        }
        if (lower.contains("amazon") || lower.contains("flipkart") || lower.contains("myntra") || lower.contains("shopping") || lower.contains("mart") || lower.contains("store") || lower.contains("grocery")) {
            return "Shopping";
        }
        if (lower.contains("electricity") || lower.contains("recharge") || lower.contains("bill") || lower.contains("wifi") || lower.contains("netflix") || lower.contains("spotify") || lower.contains("youtube") || lower.contains("broadband")) {
            return "Bills";
        }
        if (lower.contains("hospital") || lower.contains("pharmacy") || lower.contains("medical") || lower.contains("clinic") || lower.contains("doctor") || lower.contains("health")) {
            return "Health";
        }
        if (lower.contains("game") || lower.contains("movie") || lower.contains("cinema") || lower.contains("pvr") || lower.contains("bookmyshow") || lower.contains("pub")) {
            return "Entertainment";
        }
        return "Other";
    }
}
