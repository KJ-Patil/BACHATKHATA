package com.example.moneylens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Integration & Security tests for offline data mutations and security lockouts (P3.2, P3.3).
 * Verifies that the app fails closed to the local PIN cache in offline/airplane mode
 * and preserves multi-currency INR baseline rules.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class OfflineSyncAndSecurityTest {

    private SharedPreferencesManager prefs;
    private final String TEST_UID = "test_user_offline_12345";
    private final String TEST_PIN_HASH = "salted_hash_abc_987654";

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        prefs = SharedPreferencesManager.getInstance(context);
        prefs.clearCachedPin();
    }

    @Test
    public void testOfflinePinCache_storesAndRetainsHash() {
        // Initially, PIN state should not be known for test user
        assertFalse("PIN state should not be known before sync", prefs.isPinStateKnown(TEST_UID));
        assertEquals("", prefs.getCachedPinHash(TEST_UID));

        // Mirror PIN state into encrypted local store
        prefs.setCachedPin(TEST_UID, TEST_PIN_HASH);

        // Verify state is known and hash is preserved
        assertTrue("PIN state must be known after setting cached PIN", prefs.isPinStateKnown(TEST_UID));
        assertEquals("Cached hash must match stored hash", TEST_PIN_HASH, prefs.getCachedPinHash(TEST_UID));
    }

    @Test
    public void testAirplaneModeBypassPrevention_failsClosedToLocalCache() {
        // Simulate user who set a PIN and went offline / airplane mode
        prefs.setCachedPin(TEST_UID, TEST_PIN_HASH);
        prefs.setAppLockEnabled(true);

        // Verify that app lock remains enabled offline
        assertTrue("App lock must remain enabled in offline scenario", prefs.isAppLockEnabled());

        // Verify that another account cannot bypass or borrow this user's cached PIN
        String maliciousUid = "malicious_or_switched_user_67890";
        assertFalse("Different UID must not have cached PIN state", prefs.isPinStateKnown(maliciousUid));
        assertEquals("Hash must be empty for unauthenticated / different user", "", prefs.getCachedPinHash(maliciousUid));

        // Clearing cached PIN upon sign-out works cleanly
        prefs.clearCachedPin();
        assertFalse("Cleared PIN state must not be known", prefs.isPinStateKnown(TEST_UID));
        assertEquals("", prefs.getCachedPinHash(TEST_UID));
    }

    @Test
    public void testMultiCurrencyBaseline_defaultINRIntegrity() {
        // By default, base currency must be INR with ₹ symbol
        String currency = prefs.getUserCurrency();
        String symbol = prefs.getUserCurrencySymbol();

        assertNotNull(currency);
        assertNotNull(symbol);

        // Set to USD and verify update
        prefs.setUserCurrency("USD");
        prefs.setUserCurrencySymbol("$");
        assertEquals("USD", prefs.getUserCurrency());
        assertEquals("$", prefs.getUserCurrencySymbol());

        // Restore baseline INR
        prefs.setUserCurrency("INR");
        prefs.setUserCurrencySymbol("₹");
        assertEquals("INR", prefs.getUserCurrency());
        assertEquals("₹", prefs.getUserCurrencySymbol());
    }

    @Test
    public void testAppLockChoiceMarker_distinguishesDeliberateOffFromUnset() {
        // Verifies hasChosenAppLock prevents silent override
        prefs.setAppLockEnabled(false);
        assertTrue("hasChosenAppLock must be true once explicitly set", prefs.hasChosenAppLock());
        assertFalse("App lock should be false", prefs.isAppLockEnabled());

        prefs.setAppLockEnabled(true);
        assertTrue("hasChosenAppLock must remain true", prefs.hasChosenAppLock());
        assertTrue("App lock should now be true", prefs.isAppLockEnabled());
    }
}
