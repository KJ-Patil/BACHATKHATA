package com.example.bachatkhata.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.bachatkhata.Category;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CategoriesTest {

    private static Category category(String name, String type, String bucket, boolean archived) {
        Category c = new Category();
        c.setId(name.toLowerCase());
        c.setName(name);
        c.setType(type);
        c.setBucket(bucket);
        c.setArchived(archived);
        return c;
    }

    // ── BucketType.fromKey ──────────────────────────────────────────────

    @Test
    public void fromKey_parsesKnownKeysCaseInsensitively() {
        assertEquals(BucketType.NEEDS, BucketType.fromKey("needs"));
        assertEquals(BucketType.WANTS, BucketType.fromKey("WANTS"));
        assertEquals(BucketType.INVESTMENTS, BucketType.fromKey("  Investments "));
    }

    @Test
    public void fromKey_returnsNullForUnset() {
        assertNull(BucketType.fromKey(null));
        assertNull(BucketType.fromKey(""));
        assertNull(BucketType.fromKey("groceries"));
    }

    // ── Layered resolution ──────────────────────────────────────────────

    @Test
    public void layer1_userChoiceWinsOverBuiltInMap() {
        // "Travel" is a built-in want; the user has re-tagged it as a need.
        List<Category> cats = Collections.singletonList(category("Travel", "expense", "needs", false));
        assertEquals(BucketType.NEEDS, Categories.resolveBucketForCategory("Travel", cats));
    }

    @Test
    public void layer2_builtInMapUsedWhenNoExplicitBucket() {
        // Stored before buckets existed: bucket is null, so it must resolve as it always did.
        List<Category> cats = Collections.singletonList(category("Travel", "expense", null, false));
        assertEquals(BucketType.WANTS, Categories.resolveBucketForCategory("Travel", cats));
    }

    @Test
    public void layer2_worksWithNoStoredCategoriesAtAll() {
        assertEquals(BucketType.NEEDS, Categories.resolveBucketForCategory("Rent", null));
        assertEquals(BucketType.WANTS, Categories.resolveBucketForCategory("Entertainment", null));
        assertEquals(BucketType.INVESTMENTS, Categories.resolveBucketForCategory("Investment", null));
    }

    @Test
    public void layer3_unknownUserCategoryFallsBackToNeeds() {
        List<Category> cats = Collections.singletonList(category("Pet Grooming", "expense", null, false));
        assertEquals(BucketType.NEEDS, Categories.resolveBucketForCategory("Pet Grooming", cats));
    }

    @Test
    public void resolution_isCaseAndWhitespaceInsensitive() {
        List<Category> cats = Collections.singletonList(category("Shopping", "expense", null, false));
        assertEquals(BucketType.WANTS, Categories.resolveBucketForCategory("  shopping ", cats));
    }

    @Test
    public void archivedCategoriesStillResolve() {
        // Old transactions keep the name; their history must stay in the same bucket.
        List<Category> cats = Collections.singletonList(category("Gym", "expense", "wants", true));
        assertEquals(BucketType.WANTS, Categories.resolveBucketForCategory("Gym", cats));
    }

    @Test
    public void nullOrBlankCategoryNameFallsBackToNeeds() {
        assertEquals(BucketType.NEEDS, Categories.resolveBucketForCategory(null, null));
        assertEquals(BucketType.NEEDS, Categories.resolveBucketForCategory("   ", null));
    }

    // ── active() ────────────────────────────────────────────────────────

    @Test
    public void active_dropsArchivedAndFiltersByType() {
        List<Category> all = Arrays.asList(
                category("Food", "expense", null, false),
                category("Gym", "expense", null, true),      // archived
                category("Salary", "income", null, false));

        List<Category> expenses = Categories.active(all, "expense");
        assertEquals(1, expenses.size());
        assertEquals("Food", expenses.get(0).getName());

        assertEquals(2, Categories.active(all, null).size());
        assertTrue(Categories.active(new ArrayList<>(), "expense").isEmpty());
        assertTrue(Categories.active(null, "expense").isEmpty());
    }

    // ── Savings deposit categories ──────────────────────────────────────

    @Test
    public void savingsDepositCategoryRoundTripsToItsOwnBucket() {
        for (BucketType bucket : BucketType.values()) {
            String depositCategory = BucketConfig.savingsDepositCategory(bucket);
            assertEquals("deposit category for " + bucket + " must resolve back to " + bucket,
                    bucket, Categories.resolveBucketForCategory(depositCategory, null));
        }
    }

    @Test
    public void nullGoalBucketDepositsAsInvestment() {
        // Goals created before the tag existed keep the old behaviour.
        assertEquals("Investment", BucketConfig.savingsDepositCategory(null));
    }

    // ── Investment income ───────────────────────────────────────────────

    @Test
    public void investmentIncomeRecognisesReturnCategories() {
        assertTrue(BucketConfig.isInvestmentIncome("Dividend"));
        assertTrue(BucketConfig.isInvestmentIncome("  interest "));
        assertTrue(BucketConfig.isInvestmentIncome("Capital Gains"));
    }

    @Test
    public void investmentIncomeExcludesEarnedIncome() {
        // Unrecognised income is not counted, so returns are understated, never inflated.
        assertFalse(BucketConfig.isInvestmentIncome("Salary"));
        assertFalse(BucketConfig.isInvestmentIncome("Freelance"));
        assertFalse(BucketConfig.isInvestmentIncome(null));
        assertFalse(BucketConfig.isInvestmentIncome("Rental yield"));
    }
}
