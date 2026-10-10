package com.example.moneylens;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Configuration & Theme persistence tests (P3.3, §9).
 * Verifies dark mode, light mode, and system follow mode mapping and persistence.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class ThemeConfigurationTest {

    private SharedPreferencesManager prefs;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        prefs = SharedPreferencesManager.getInstance(context);
    }

    @Test
    public void testThemeMode_darkModeMappingAndPersistence() {
        // Apply Dark mode
        prefs.setThemeMode("Dark");
        assertEquals("Dark", prefs.getThemeMode());

        SharedPreferencesManager.applyThemeMode("Dark");
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode());
    }

    @Test
    public void testThemeMode_lightModeMappingAndPersistence() {
        // Apply Light mode
        prefs.setThemeMode("Light");
        assertEquals("Light", prefs.getThemeMode());

        SharedPreferencesManager.applyThemeMode("Light");
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode());
    }

    @Test
    public void testThemeMode_systemModeMapping() {
        // Apply System follow mode
        prefs.setThemeMode("System");
        assertEquals("System", prefs.getThemeMode());

        SharedPreferencesManager.applyThemeMode("System");
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.getDefaultNightMode());
    }
}
