package com.example.bachatkhata.domain;

/**
 * The three spending buckets of the 50/30/20 budgeting rule.
 *
 * <p>Buckets are <strong>expense-only</strong>. Income is grouped by source instead
 * (earned / investment / passive), which is a separate concern.
 *
 * <p>Persisted as the lowercase {@link #key()} so the stored value stays readable in
 * Firestore and survives renaming the enum constants.
 */
public enum BucketType {

    NEEDS("needs", "Needs"),
    WANTS("wants", "Wants"),
    INVESTMENTS("investments", "Investments");

    private final String key;
    private final String label;

    BucketType(String key, String label) {
        this.key = key;
        this.label = label;
    }

    /** Stable storage key, e.g. {@code "needs"}. */
    public String key() {
        return key;
    }

    /** Human-readable label for the UI, e.g. {@code "Needs"}. */
    public String label() {
        return label;
    }

    /**
     * Parses a stored key back into a bucket.
     *
     * @return the matching bucket, or {@code null} when the key is null, blank or
     *         unrecognised — callers treat that as "not explicitly set" and fall back.
     */
    public static BucketType fromKey(String key) {
        if (key == null) return null;
        String normalized = key.trim().toLowerCase();
        for (BucketType b : values()) {
            if (b.key.equals(normalized)) return b;
        }
        return null;
    }
}
