package com.example.bachatkhata;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Map;

public class DefaultDataSeeder {

    public static void seedDefaultCategories(String uid, OnSuccessListener<Void> listener) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        WriteBatch batch = db.batch();
        CollectionReference catColRef = db.collection("users").document(uid).collection("categories");

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

        // Seed 3 Income Categories
        addCategoryToBatch(batch, catColRef, "Salary", "💰", "#1D9E75", "income");
        addCategoryToBatch(batch, catColRef, "Freelance", "💻", "#5DCAA5", "income");
        addCategoryToBatch(batch, catColRef, "Other income", "📥", "#B4B2A9", "income");

        batch.commit()
                .addOnSuccessListener(aVoid -> listener.onSuccess(null))
                .addOnFailureListener(e -> listener.onSuccess(null)); // Fallback, proceed anyway to not block user
    }

    private static void addCategoryToBatch(WriteBatch batch, CollectionReference col, String name, String icon, String color, String type) {
        DocumentReference docRef = col.document();
        Map<String, Object> category = new HashMap<>();
        category.put("id", docRef.getId());
        category.put("name", name);
        category.put("icon", icon);
        category.put("color", color);
        category.put("type", type);
        category.put("isDefault", true);
        batch.set(docRef, category);
    }
}
