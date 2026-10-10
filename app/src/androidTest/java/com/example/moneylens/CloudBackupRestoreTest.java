package com.example.moneylens;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.google.firebase.Timestamp;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Integration & Schema tests for Cloud Backup and Restore (P3.2).
 * Verifies financial collections coverage, BackupInfo model fidelity, and backup UI.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class CloudBackupRestoreTest {

    @Test
    public void testBackupSchema_coversAllFinancialCollections() {
        // Must cover every financial collection that clear-data or restore touches
        Set<String> collections = new HashSet<>(Arrays.asList(CloudBackupManager.BACKED_UP_COLLECTIONS));

        assertTrue("Must backup transactions", collections.contains("transactions"));
        assertTrue("Must backup budgets", collections.contains("budgets"));
        assertTrue("Must backup savings_goals", collections.contains("savings_goals"));
        assertTrue("Must backup categories", collections.contains("categories"));
        assertTrue("Must backup customers", collections.contains("customers"));
        assertTrue("Must backup customer_txns", collections.contains("customer_txns"));
        assertTrue("Must backup emis", collections.contains("emis"));
        assertTrue("Must backup bills", collections.contains("bills"));
        assertTrue("Must backup subscriptions", collections.contains("subscriptions"));
    }

    @Test
    public void testBackupInfo_modelIntegrity() {
        Timestamp now = Timestamp.now();
        CloudBackupManager.BackupInfo info = new CloudBackupManager.BackupInfo(
                "backup_test_id_99",
                "Pre-Cleanup Snapshot",
                42,
                now
        );

        assertEquals("backup_test_id_99", info.id);
        assertEquals("Pre-Cleanup Snapshot", info.label);
        assertEquals(42, info.count);
        assertEquals(now, info.createdAt);
    }

    @Test
    public void testCloudBackupActivity_initialViews() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, CloudBackupActivity.class);

        try (ActivityScenario<CloudBackupActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withId(R.id.btnCreateBackup)).check(matches(isDisplayed()));
            onView(withId(R.id.rvBackups)).check(matches(isDisplayed()));
        }
    }
}
