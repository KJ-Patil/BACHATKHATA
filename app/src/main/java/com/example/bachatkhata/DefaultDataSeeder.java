package com.example.bachatkhata;

import com.example.bachatkhata.domain.BucketType;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Map;

public class DefaultDataSeeder {

    public static void seedDefaultData(String uid, OnSuccessListener<Void> successListener, OnFailureListener failureListener) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        WriteBatch batch = db.batch();

        CollectionReference catColRef = db.collection("users").document(uid).collection("categories");
        CollectionReference billColRef = db.collection("users").document(uid).collection("bills");

        // Seed 12 Expense Categories
        addCategoryToBatch(batch, catColRef, "Food", "🍛", "#EF9F27", "expense");
        addCategoryToBatch(batch, catColRef, "Transport", "🚌", "#3B82F6", "expense");
        addCategoryToBatch(batch, catColRef, "Shopping", "🛍️", "#E24B4A", "expense");
        addCategoryToBatch(batch, catColRef, "Bills", "💡", "#7C6FE0", "expense");
        addCategoryToBatch(batch, catColRef, "Health", "🏥", "#5DCAA5", "expense");
        addCategoryToBatch(batch, catColRef, "Entertainment", "🎮", "#F0997B", "expense");
        addCategoryToBatch(batch, catColRef, "Education", "📚", "#AFA9EC", "expense");
        addCategoryToBatch(batch, catColRef, "Rent", "🏠", "#888780", "expense");
        addCategoryToBatch(batch, catColRef, "Personal care", "💆", "#F4C0D1", "expense");
        addCategoryToBatch(batch, catColRef, "Travel", "✈️", "#85B7EB", "expense");
        addCategoryToBatch(batch, catColRef, "Gifts", "🎁", "#ED93B1", "expense");
        addCategoryToBatch(batch, catColRef, "Other", "📦", "#B4B2A9", "expense");

        // Categories the app writes on the user's behalf when a savings goal is topped up.
        // Seeded so those rows render with a real icon and colour instead of a placeholder.
        addCategoryToBatch(batch, catColRef, "Investment", "📈", "#1D9E75", "expense",
                BucketType.INVESTMENTS.key());
        addCategoryToBatch(batch, catColRef, "Savings (Needs)", "🏦", "#3B82F6", "expense",
                BucketType.NEEDS.key());
        addCategoryToBatch(batch, catColRef, "Savings (Wants)", "🐖", "#F0997B", "expense",
                BucketType.WANTS.key());

        // Seed 3 Income Categories
        addCategoryToBatch(batch, catColRef, "Salary", "💰", "#1D9E75", "income");
        addCategoryToBatch(batch, catColRef, "Freelance", "💻", "#5DCAA5", "income");
        addCategoryToBatch(batch, catColRef, "Other income", "📥", "#B4B2A9", "income");

        // Seed 3 default bills: Electricity (28th), Mobile Recharge (1st), Internet (5th)
        addBillToBatch(batch, billColRef, "Electricity", 0.0, 28, "Bills");
        addBillToBatch(batch, billColRef, "Mobile Recharge", 0.0, 1, "Bills");
        addBillToBatch(batch, billColRef, "Internet", 0.0, 5, "Bills");

        batch.commit()
                .addOnSuccessListener(successListener)
                .addOnFailureListener(failureListener);
    }

    private static void addCategoryToBatch(WriteBatch batch, CollectionReference col, String name, String icon, String color, String type) {
        addCategoryToBatch(batch, col, name, icon, color, type, null);
    }

    /**
     * @param bucket 50/30/20 bucket key, or null to let {@code Categories.resolveBucketForCategory}
     *               fall back to the built-in map — which is what the 12 defaults rely on.
     */
    private static void addCategoryToBatch(WriteBatch batch, CollectionReference col, String name, String icon, String color, String type, String bucket) {
        DocumentReference docRef = col.document();
        Map<String, Object> category = new HashMap<>();
        category.put("id", docRef.getId());
        category.put("name", name);
        category.put("icon", icon);
        category.put("color", color);
        category.put("type", type);
        category.put("isDefault", true);
        category.put("archived", false);
        category.put("bucket", bucket);
        batch.set(docRef, category);
    }

    private static void addBillToBatch(WriteBatch batch, CollectionReference col, String name, double amount, int dueDay, String category) {
        DocumentReference docRef = col.document();
        Map<String, Object> bill = new HashMap<>();
        bill.put("id", docRef.getId());
        bill.put("name", name);
        bill.put("amount", amount);
        bill.put("dueDay", dueDay);
        bill.put("category", category);
        bill.put("isRecurring", true);
        bill.put("lastPaidDate", null);
        bill.put("isActive", true);
        bill.put("isDefault", true);
        batch.set(docRef, bill);
    }
}
