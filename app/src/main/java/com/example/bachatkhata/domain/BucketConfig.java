package com.example.bachatkhata.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static configuration behind the 50/30/20 rule: the default split, the built-in
 * category&nbsp;&rarr;&nbsp;bucket fallback map, and the categories savings deposits are
 * logged under.
 *
 * <p>This is the <em>fallback</em> layer only. Never call {@link #builtInBucketFor} directly
 * from feature code — go through {@link Categories#resolveBucketForCategory}, which honours
 * the user's own choice first. Reading this map directly misfiles every user-created
 * category as <em>needs</em>.
 */
public final class BucketConfig {

    private BucketConfig() {}

    /** The classic 50/30/20 allocation, as fractions of monthly income. */
    public static final Map<BucketType, Double> DEFAULT_PERCENTAGES;

    /** Lowercased built-in category name &rarr; bucket. */
    private static final Map<String, BucketType> CATEGORY_BUCKET_MAP;

    /** Bucket &rarr; the expense category a savings deposit into that bucket is logged under. */
    public static final Map<BucketType, String> SAVINGS_DEPOSIT_CATEGORY;

    static {
        EnumMap<BucketType, Double> pct = new EnumMap<>(BucketType.class);
        pct.put(BucketType.NEEDS, 0.50);
        pct.put(BucketType.WANTS, 0.30);
        pct.put(BucketType.INVESTMENTS, 0.20);
        DEFAULT_PERCENTAGES = Collections.unmodifiableMap(pct);

        EnumMap<BucketType, String> deposits = new EnumMap<>(BucketType.class);
        deposits.put(BucketType.NEEDS, "Savings (Needs)");
        deposits.put(BucketType.WANTS, "Savings (Wants)");
        // Unchanged from before buckets existed, so goals created back then keep classifying
        // their deposits exactly as they always did.
        deposits.put(BucketType.INVESTMENTS, "Investment");
        SAVINGS_DEPOSIT_CATEGORY = Collections.unmodifiableMap(deposits);

        Map<String, BucketType> map = new LinkedHashMap<>();
        // The 12 seeded expense categories (see DefaultDataSeeder).
        map.put("food", BucketType.NEEDS);
        map.put("transport", BucketType.NEEDS);
        map.put("bills", BucketType.NEEDS);
        map.put("health", BucketType.NEEDS);
        map.put("education", BucketType.NEEDS);
        map.put("rent", BucketType.NEEDS);
        map.put("shopping", BucketType.WANTS);
        map.put("entertainment", BucketType.WANTS);
        map.put("personal care", BucketType.WANTS);
        map.put("travel", BucketType.WANTS);
        map.put("gifts", BucketType.WANTS);
        map.put("other", BucketType.NEEDS);

        // Categories the app writes on the user's behalf.
        map.put("groceries", BucketType.NEEDS);
        map.put("utilities", BucketType.NEEDS);
        map.put("insurance", BucketType.NEEDS);
        map.put("housing", BucketType.NEEDS);
        map.put("ledger", BucketType.NEEDS);
        map.put("emi", BucketType.NEEDS);
        map.put("loan", BucketType.NEEDS);
        map.put("subscriptions", BucketType.WANTS);
        map.put("dining out", BucketType.WANTS);
        map.put("savings (needs)", BucketType.NEEDS);
        map.put("savings (wants)", BucketType.WANTS);
        map.put("investment", BucketType.INVESTMENTS);
        map.put("investments", BucketType.INVESTMENTS);
        map.put("savings", BucketType.INVESTMENTS);
        map.put("mutual funds", BucketType.INVESTMENTS);
        map.put("sip", BucketType.INVESTMENTS);
        map.put("round-up savings", BucketType.INVESTMENTS);

        CATEGORY_BUCKET_MAP = Collections.unmodifiableMap(new HashMap<>(map));
    }

    /**
     * Income categories that represent a return <em>on</em> investments rather than earned
     * or passive income.
     *
     * <p>Interim heuristic: matched by name until income source groups (Earned / Investment /
     * Passive) exist. Unrecognised income is simply not counted as a return, which
     * understates rather than inflates the figure.
     */
    private static final java.util.Set<String> INVESTMENT_INCOME_CATEGORIES = Collections.unmodifiableSet(
            new java.util.HashSet<>(java.util.Arrays.asList(
                    "investment", "investments", "investment returns", "returns",
                    "dividend", "dividends", "interest", "interest income",
                    "capital gains", "mutual funds", "stocks", "sip returns")));

    /** True when income under this category name is a return on invested money. */
    public static boolean isInvestmentIncome(String categoryName) {
        if (categoryName == null) return false;
        return INVESTMENT_INCOME_CATEGORIES.contains(categoryName.trim().toLowerCase());
    }

    /**
     * The built-in bucket for a category name, matched case-insensitively.
     *
     * @return the mapped bucket, or {@code null} when the name isn't a known built-in.
     */
    public static BucketType builtInBucketFor(String categoryName) {
        if (categoryName == null) return null;
        return CATEGORY_BUCKET_MAP.get(categoryName.trim().toLowerCase());
    }

    /** The expense category a deposit into {@code bucket} should be logged under. */
    public static String savingsDepositCategory(BucketType bucket) {
        BucketType b = bucket != null ? bucket : BucketType.INVESTMENTS;
        return SAVINGS_DEPOSIT_CATEGORY.get(b);
    }
}
