package com.example.moneylens;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.google.android.material.textfield.TextInputLayout;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * UI tests for Login & Onboarding journey (P3.1).
 * Verifies email/password validation, social login options, and onboarding screens.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class LoginOnboardingFlowTest {

    @Test
    public void testLogin_initialUiElements() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, LoginActivity.class);

        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withId(R.id.etEmail)).check(matches(isDisplayed()));
            onView(withId(R.id.etPassword)).check(matches(isDisplayed()));
            onView(withId(R.id.btnSignIn)).check(matches(isDisplayed()));
            onView(withId(R.id.btnGoogleSignIn)).check(matches(isDisplayed()));
            onView(withId(R.id.btnPhoneSignIn)).check(matches(isDisplayed()));
            onView(withId(R.id.btnForgotPassword)).check(matches(isDisplayed()));
            onView(withId(R.id.btnGoToRegister)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void testLogin_emptyCredentials_showsValidationErrors() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, LoginActivity.class);

        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(intent)) {
            // Click sign in immediately with empty inputs
            onView(withId(R.id.btnSignIn)).perform(click());

            scenario.onActivity(activity -> {
                TextInputLayout tilEmail = activity.findViewById(R.id.tilEmail);
                Assert.assertNotNull("Email layout should have validation error", tilEmail.getError());
            });
        }
    }

    @Test
    public void testLogin_invalidEmail_showsFormatError() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, LoginActivity.class);

        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withId(R.id.etEmail)).perform(typeText("invalid-email-string"), closeSoftKeyboard());
            onView(withId(R.id.etPassword)).perform(typeText("12345678"), closeSoftKeyboard());
            onView(withId(R.id.btnSignIn)).perform(click());

            scenario.onActivity(activity -> {
                TextInputLayout tilEmail = activity.findViewById(R.id.tilEmail);
                Assert.assertNotNull("Email layout should show invalid format error", tilEmail.getError());
            });
        }
    }

    @Test
    public void testOnboarding_initialDisplay() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, OnboardingActivity.class);

        try (ActivityScenario<OnboardingActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withId(R.id.btnSkip)).check(matches(isDisplayed()));
            onView(withId(R.id.viewPagerOnboarding)).check(matches(isDisplayed()));
            onView(withId(R.id.btnNext)).check(matches(isDisplayed()));
        }
    }
}
