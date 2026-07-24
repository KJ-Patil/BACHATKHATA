package com.example.bachatkhata;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.bachatkhata.domain.MoneyRule;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Reads and writes the Budgeting Rule settings — monthly take-home income and the
 * user's percentage split.
 *
 * <p>Prefs are the source of truth for reads so the screen works offline and on first
 * paint; Firestore is a mirror on {@code users/{uid}} so the settings follow the account
 * to a new device. These are deliberately synced: the split changes what every bucket
 * report says, and silently resetting it to 50/30/20 on a new device would show different
 * numbers with no error.
 */
public final class MoneyRuleSettings {

    private static final String FIELD_INCOME = "monthlyIncome";
    private static final String FIELD_NEEDS = "ruleNeeds";
    private static final String FIELD_WANTS = "ruleWants";
    private static final String FIELD_INVESTMENTS = "ruleInvestments";

    private MoneyRuleSettings() {}

    public static double getIncome(@NonNull Context context) {
        return SharedPreferencesManager.getInstance(context).getMonthlyIncome();
    }

    public static void setIncome(@NonNull Context context, String uid, double income) {
        SharedPreferencesManager.getInstance(context).setMonthlyIncome(income);
        if (uid == null) return;
        Map<String, Object> update = new HashMap<>();
        update.put(FIELD_INCOME, Math.max(0, income));
        mirror(uid, update);
    }

    public static MoneyRule.Split getSplit(@NonNull Context context) {
        SharedPreferencesManager prefs = SharedPreferencesManager.getInstance(context);
        MoneyRule.Split stored = new MoneyRule.Split(
                prefs.getRuleNeeds(), prefs.getRuleWants(), prefs.getRuleInvestments());
        // A split that doesn't total 100 can only come from corrupted state; don't let it
        // through, or every allocation on screen is quietly wrong.
        return stored.isValid() ? stored : MoneyRule.Split.defaultSplit();
    }

    /**
     * @return false (and writes nothing) when the split doesn't total exactly 100.
     */
    public static boolean setSplit(@NonNull Context context, String uid, MoneyRule.Split split) {
        if (split == null || !split.isValid()) return false;

        SharedPreferencesManager.getInstance(context)
                .setRuleSplit(split.needs, split.wants, split.investments);
        if (uid != null) {
            Map<String, Object> update = new HashMap<>();
            update.put(FIELD_NEEDS, split.needs);
            update.put(FIELD_WANTS, split.wants);
            update.put(FIELD_INVESTMENTS, split.investments);
            mirror(uid, update);
        }
        return true;
    }

    /**
     * Pulls the cloud copy into prefs. Call after sign-in so a new device picks up the
     * user's real split rather than defaulting to 50/30/20.
     */
    public static void loadFromFirestore(@NonNull Context context, String uid, Runnable onLoaded) {
        if (uid == null) {
            if (onLoaded != null) onLoaded.run();
            return;
        }
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        SharedPreferencesManager prefs = SharedPreferencesManager.getInstance(context);
                        Double income = doc.getDouble(FIELD_INCOME);
                        if (income != null) prefs.setMonthlyIncome(income);

                        Long needs = doc.getLong(FIELD_NEEDS);
                        Long wants = doc.getLong(FIELD_WANTS);
                        Long investments = doc.getLong(FIELD_INVESTMENTS);
                        if (needs != null && wants != null && investments != null) {
                            MoneyRule.Split remote = new MoneyRule.Split(
                                    needs.intValue(), wants.intValue(), investments.intValue());
                            if (remote.isValid()) {
                                prefs.setRuleSplit(remote.needs, remote.wants, remote.investments);
                            }
                        }
                    }
                    if (onLoaded != null) onLoaded.run();
                })
                .addOnFailureListener(e -> {
                    // Offline or unreadable: the local prefs still hold the last known good
                    // values, so carry on rather than blocking the screen.
                    if (onLoaded != null) onLoaded.run();
                });
    }

    private static void mirror(String uid, Map<String, Object> fields) {
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .set(fields, SetOptions.merge());
    }
}
