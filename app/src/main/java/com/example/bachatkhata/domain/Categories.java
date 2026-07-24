package com.example.bachatkhata.domain;

import com.example.bachatkhata.Category;

import java.util.ArrayList;
import java.util.List;

/**
 * The app-wide answer to "which bucket does this spending belong to?".
 *
 * <p>Categories are identified <strong>by name</strong> throughout the app
 * ({@code Transaction.category}, budget keys); the Firestore document id is internal.
 */
public final class Categories {

    private Categories() {}

    /**
     * Resolves the 50/30/20 bucket for a category name. Every spending rollup must go
     * through here rather than reading {@link BucketConfig} directly.
     *
     * <p>Resolution is layered, most specific first:
     * <ol>
     *   <li>the bucket the user explicitly picked for their own category;</li>
     *   <li>the built-in fallback map, for seeded and app-generated names;</li>
     *   <li>{@link BucketType#NEEDS}, as a last resort.</li>
     * </ol>
     *
     * <p>Layer 2 is why no data migration is needed: a category stored before {@code bucket}
     * existed resolves exactly as it did when the map was the only lookup. Archived
     * categories still resolve — old transactions keep their category name, and their
     * history has to stay in the bucket it was always counted under.
     *
     * @param categoryName the name stored on the transaction or budget
     * @param categories   the user's categories; may be null or empty
     */
    public static BucketType resolveBucketForCategory(String categoryName, List<Category> categories) {
        if (categoryName == null || categoryName.trim().isEmpty()) return BucketType.NEEDS;
        String target = categoryName.trim();

        // Layer 1 — the user's own choice wins, archived or not.
        if (categories != null) {
            for (Category c : categories) {
                if (c == null || c.getName() == null) continue;
                if (!c.getName().trim().equalsIgnoreCase(target)) continue;
                BucketType explicit = BucketType.fromKey(c.getBucket());
                if (explicit != null) return explicit;
                break; // matched the category but it has no bucket set — fall through
            }
        }

        // Layer 2 — built-in names.
        BucketType builtIn = BucketConfig.builtInBucketFor(target);
        if (builtIn != null) return builtIn;

        // Layer 3 — unknown, user-created and untagged.
        return BucketType.NEEDS;
    }

    /**
     * Categories that should appear in pickers: not archived, optionally filtered by type.
     *
     * @param type "expense", "income", or null for both
     */
    public static List<Category> active(List<Category> all, String type) {
        List<Category> out = new ArrayList<>();
        if (all == null) return out;
        for (Category c : all) {
            if (c == null || c.getArchived()) continue;
            if (type != null && c.getType() != null && !type.equalsIgnoreCase(c.getType())) continue;
            out.add(c);
        }
        return out;
    }
}
