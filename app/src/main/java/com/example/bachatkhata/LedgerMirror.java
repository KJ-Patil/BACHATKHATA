package com.example.bachatkhata;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.Date;

/**
 * Mirrors khata (ledger) entries into the main transaction list.
 *
 * <p>Money moving through the notebook is real money leaving and entering the
 * user's hands, so it belongs in the main ledger too — otherwise the balance card,
 * the monthly totals and the calendar all under-report by exactly the amount of
 * the khata activity. Mirrored rows carry the reserved {@link #LEDGER_CATEGORY}
 * category, which is what lets a screen that would otherwise double-count (the
 * calendar shows khata entries in their own section) filter them back out.
 *
 * <p>Direction follows the entry type: <em>gave</em> is money out (an expense),
 * <em>got</em> is money in (income).
 */
public final class LedgerMirror {

    /**
     * Reserved category for a mirrored khata entry. Screens that render ledger
     * entries separately must exclude transactions in this category from their
     * transaction totals, or the same rupee is counted twice.
     */
    public static final String LEDGER_CATEGORY = "Ledger";

    /** Entry type for money the user handed out — mirrors to an expense. */
    public static final String TYPE_GAVE = "gave";

    /** Entry type for money the user received — mirrors to income. */
    public static final String TYPE_GOT = "got";

    private LedgerMirror() {
    }

    /** True when {@code category} marks a transaction that mirrors a khata entry. */
    public static boolean isMirrored(String category) {
        return LEDGER_CATEGORY.equalsIgnoreCase(category);
    }

    /**
     * Builds the transaction that mirrors a khata entry. The caller owns the write
     * so it can be batched atomically with the entry itself.
     *
     * @param txId      id of the transaction document being written
     * @param entryType {@link #TYPE_GAVE} or {@link #TYPE_GOT}
     * @param note      the entry's note, used as the transaction note
     */
    public static Transaction buildTransaction(String txId, double amount, String entryType,
                                               String counterpartyName, String note, Date date) {
        boolean isGave = TYPE_GAVE.equals(entryType);
        CurrencyManager currency = CurrencyManager.getInstance();

        String description = (note == null || note.trim().isEmpty())
                ? (isGave ? "You gave " : "You got ") + counterpartyName
                : note.trim() + " · " + counterpartyName;

        return new Transaction(
                txId,
                amount,
                isGave ? "expense" : "income",
                LEDGER_CATEGORY,
                description,
                date,
                "Cash",
                currency.getCurrentCurrencyCode(),
                currency.getCurrentCurrencySymbol(),
                "ledger",
                null,
                Timestamp.now());
    }

    /**
     * Queues the writes for one khata entry: the entry itself, its mirrored
     * transaction, and the customer's running balance.
     *
     * <p>All three go in a single batch on purpose — a balance that moved without
     * its entry, or an entry without its transaction, is a discrepancy the user has
     * no way to find or fix from the UI.
     *
     * @return the entry, with {@code txId} already pointing at the mirrored row
     */
    public static CustomerTransaction queueEntry(FirebaseFirestore firestore, WriteBatch batch,
                                                 String uid, Customer customer, double amount,
                                                 String entryType, String note, Date date) {
        DocumentReference entryRef = firestore.collection("users").document(uid)
                .collection("customer_txns").document();
        DocumentReference txRef = firestore.collection("users").document(uid)
                .collection("transactions").document();

        CustomerTransaction entry = new CustomerTransaction(
                entryRef.getId(), customer.getId(), amount, note,
                new Timestamp(date), entryType, txRef.getId());
        batch.set(entryRef, entry.toMap());

        Transaction mirrored = buildTransaction(
                txRef.getId(), amount, entryType, customer.getName(), note, date);
        batch.set(txRef, mirrored.toMap());

        // "gave" grows what they owe you; "got" pays it down.
        double balanceChange = TYPE_GAVE.equals(entryType) ? amount : -amount;
        DocumentReference customerRef = firestore.collection("users").document(uid)
                .collection("customers").document(customer.getId());
        batch.update(customerRef, "balance", FieldValue.increment(balanceChange));

        return entry;
    }
}
