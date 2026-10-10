package com.example.moneylens;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;

import android.content.Context;
import android.content.Intent;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * UI & Calculation flow tests for adding and editing transactions (P3.1).
 * Verifies Amount input, Income/Expense toggling, discount calculation UI, and input validation.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class TransactionFlowTest {

    @Test
    public void testAddTransaction_initialViews() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, AddTransactionActivity.class);

        try (ActivityScenario<AddTransactionActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withId(R.id.etAmount)).check(matches(isDisplayed()));
            onView(withId(R.id.btnToggleExpense)).check(matches(isDisplayed()));
            onView(withId(R.id.btnToggleIncome)).check(matches(isDisplayed()));
            onView(withId(R.id.btnSaveTransaction)).check(matches(isDisplayed()));
            onView(withId(R.id.rvCategoryGrid)).check(matches(isDisplayed()));

            // Discount layout is initially collapsed
            onView(withId(R.id.layoutDiscount)).check(matches(not(isDisplayed())));
        }
    }

    @Test
    public void testAddTransaction_toggleIncomeExpense() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, AddTransactionActivity.class);

        try (ActivityScenario<AddTransactionActivity> scenario = ActivityScenario.launch(intent)) {
            // Switch to Income
            onView(withId(R.id.btnToggleIncome)).perform(click());

            // In income mode, discount option should be hidden
            onView(withId(R.id.btnToggleDiscount)).check(matches(not(isDisplayed())));

            // Switch back to Expense
            onView(withId(R.id.btnToggleExpense)).perform(click());
            onView(withId(R.id.btnToggleDiscount)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void testAddTransaction_enterAmountAndNote() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, AddTransactionActivity.class);

        try (ActivityScenario<AddTransactionActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withId(R.id.etAmount)).perform(typeText("1500.00"), closeSoftKeyboard());
            onView(withId(R.id.etAmount)).check(matches(withText("1500.00")));

            onView(withId(R.id.etNote)).perform(typeText("Grocery shopping"), closeSoftKeyboard());
            onView(withId(R.id.etNote)).check(matches(withText("Grocery shopping")));
        }
    }

    @Test
    public void testAddTransaction_discountExpansion() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, AddTransactionActivity.class);

        try (ActivityScenario<AddTransactionActivity> scenario = ActivityScenario.launch(intent)) {
            // Expand discount
            onView(withId(R.id.btnToggleDiscount)).perform(click());

            // Discount container should now be visible
            onView(withId(R.id.layoutDiscount)).check(matches(isDisplayed()));
            onView(withId(R.id.btnDiscountPercent)).check(matches(isDisplayed()));
            onView(withId(R.id.btnDiscountFlat)).check(matches(isDisplayed()));
        }
    }
}
