package com.example.bachatkhata;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Help screen's FAQ content and its search.
 *
 * <p>Entries are built from string resources so they translate with the rest of
 * the app rather than being hardcoded English.
 */
public final class FaqCatalog {

    private FaqCatalog() {
    }

    public static class Entry {
        public final String question;
        public final String answer;
        /** Collapsed by default; the Help screen toggles this in place. */
        public boolean expanded;

        Entry(String question, String answer) {
            this.question = question;
            this.answer = answer;
        }
    }

    public static List<Entry> all(Context context) {
        List<Entry> entries = new ArrayList<>();
        entries.add(entry(context, R.string.faq_q_add_txn, R.string.faq_a_add_txn));
        entries.add(entry(context, R.string.faq_q_discount, R.string.faq_a_discount));
        entries.add(entry(context, R.string.faq_q_buckets, R.string.faq_a_buckets));
        entries.add(entry(context, R.string.faq_q_rule, R.string.faq_a_rule));
        entries.add(entry(context, R.string.faq_q_budget_vs_rule, R.string.faq_a_budget_vs_rule));
        entries.add(entry(context, R.string.faq_q_sms, R.string.faq_a_sms));
        entries.add(entry(context, R.string.faq_q_ledger, R.string.faq_a_ledger));
        entries.add(entry(context, R.string.faq_q_reminder, R.string.faq_a_reminder));
        entries.add(entry(context, R.string.faq_q_family, R.string.faq_a_family));
        entries.add(entry(context, R.string.faq_q_backup, R.string.faq_a_backup));
        entries.add(entry(context, R.string.faq_q_offline, R.string.faq_a_offline));
        entries.add(entry(context, R.string.faq_q_pin, R.string.faq_a_pin));
        entries.add(entry(context, R.string.faq_q_currency, R.string.faq_a_currency));
        entries.add(entry(context, R.string.faq_q_export, R.string.faq_a_export));
        entries.add(entry(context, R.string.faq_q_clear_data, R.string.faq_a_clear_data));
        return entries;
    }

    private static Entry entry(Context context, int questionRes, int answerRes) {
        return new Entry(context.getString(questionRes), context.getString(answerRes));
    }

    /**
     * Case-insensitive substring match over both question and answer — searching
     * "backup" should surface the entry even when the word only appears in the
     * answer text.
     */
    public static List<Entry> search(List<Entry> entries, String query) {
        if (query == null || query.trim().isEmpty()) return entries;

        String needle = query.trim().toLowerCase(Locale.getDefault());
        List<Entry> matches = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.question.toLowerCase(Locale.getDefault()).contains(needle)
                    || entry.answer.toLowerCase(Locale.getDefault()).contains(needle)) {
                matches.add(entry);
            }
        }
        return matches;
    }
}
