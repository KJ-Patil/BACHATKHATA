package com.example.moneylens;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * UI & Flow tests for the complete 4-mode PIN Lock lifecycle (P3.1, §6.2b).
 * Verifies Setup, Verify, Change modes, error shake triggers, and switch account UI.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class PinLockFlowTest {

    @Test
    public void testSetupMode_initialDisplay() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, PinSetupActivity.class);
        intent.putExtra("pin_mode", "setup");

        try (ActivityScenario<PinSetupActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withId(R.id.txtPinTitle))
                    .check(matches(isDisplayed()))
                    .check(matches(withText(R.string.pin_create_title)));

            onView(withId(R.id.etPin)).check(matches(isDisplayed()));
            onView(withId(R.id.btnUnlock)).check(matches(isDisplayed()))
                    .check(matches(withText(R.string.pin_continue)));

            // Forgot and Switch Account buttons must not show in SETUP mode
            onView(withId(R.id.btnForgotPin)).check(matches(not(isDisplayed())));
            onView(withId(R.id.btnSwitchAccount)).check(matches(not(isDisplayed())));
        }
    }

    @Test
    public void testSetupMode_pinMismatchShowsError() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, PinSetupActivity.class);
        intent.putExtra("pin_mode", "setup");

        try (ActivityScenario<PinSetupActivity> scenario = ActivityScenario.launch(intent)) {
            // Step 1: Enter initial 4-digit PIN
            onView(withId(R.id.etPin)).perform(typeText("1234"), closeSoftKeyboard());
            onView(withId(R.id.btnUnlock)).perform(click());

            // Should prompt for confirmation
            onView(withId(R.id.txtPinTitle)).check(matches(withText(R.string.pin_confirm_title)));

            // Step 2: Enter mismatched PIN
            onView(withId(R.id.etPin)).perform(typeText("9999"), closeSoftKeyboard());
            onView(withId(R.id.btnUnlock)).perform(click());

            // Should show mismatch error on tilPin
            scenario.onActivity(activity -> {
                // Verify error was set
                CharSequence error = activity.findViewById(R.id.tilPin) != null
                        ? ((com.google.android.material.textfield.TextInputLayout) activity.findViewById(R.id.tilPin)).getError()
                        : null;
                org.junit.Assert.assertNotNull("Error should be shown on PIN mismatch", error);
            });
        }
    }

    @Test
    public void testVerifyMode_initialDisplayAndOptions() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, PinSetupActivity.class);
        intent.putExtra("pin_mode", "verify");

        try (ActivityScenario<PinSetupActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withId(R.id.txtPinTitle))
                    .check(matches(isDisplayed()))
                    .check(matches(withText(R.string.pin_verify_title)));

            onView(withId(R.id.btnUnlock))
                    .check(matches(isDisplayed()))
                    .check(matches(withText(R.string.pin_unlock)));

            // Forgot PIN and Switch Account must be visible on lock screen
            onView(withId(R.id.btnForgotPin)).check(matches(isDisplayed()));
            onView(withId(R.id.btnSwitchAccount)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void testVerifyMode_tooShortPinTriggersError() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, PinSetupActivity.class);
        intent.putExtra("pin_mode", "verify");

        try (ActivityScenario<PinSetupActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withId(R.id.etPin)).perform(typeText("12"), closeSoftKeyboard());
            onView(withId(R.id.btnUnlock)).perform(click());

            scenario.onActivity(activity -> {
                CharSequence error = ((com.google.android.material.textfield.TextInputLayout)
                        activity.findViewById(R.id.tilPin)).getError();
                org.junit.Assert.assertNotNull("PIN too short should show error", error);
            });
        }
    }

    @Test
    public void testChangeMode_initialDisplay() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, PinSetupActivity.class);
        intent.putExtra("pin_mode", "change");

        try (ActivityScenario<PinSetupActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withId(R.id.txtPinTitle))
                    .check(matches(isDisplayed()))
                    .check(matches(withText(R.string.pin_change_title)));

            onView(withId(R.id.txtPinSubtitle))
                    .check(matches(isDisplayed()))
                    .check(matches(withText(R.string.pin_change_current_subtitle)));

            onView(withId(R.id.btnForgotPin)).check(matches(isDisplayed()));
            onView(withId(R.id.btnSwitchAccount)).check(matches(not(isDisplayed())));
        }
    }
}
