package com.example.bachatkhata;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.PropertyName;

import java.io.Serializable;

public class Category implements Serializable {

    private String id;
    private String name;
    private String icon;
    private String color; // hex color string
    private String type; // "income" or "expense"
    private boolean isDefault;
    /**
     * Archived categories are hidden from pickers but still resolve for reporting — old
     * transactions keep their category name and their history must stay classified.
     */
    private boolean archived;
    /**
     * Expense-only 50/30/20 bucket: "needs" | "wants" | "investments".
     * Null on categories created before buckets existed; {@link BucketConfig} falls back.
     */
    private String bucket;

    public Category() {
        // Required empty public constructor for Firestore
    }

    public Category(String id, String name, String icon, String color, String type, boolean isDefault) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.color = color;
        this.type = type;
        this.isDefault = isDefault;
    }

    @PropertyName("id")
    public String getId() {
        return id;
    }

    @PropertyName("id")
    public void setId(String id) {
        this.id = id;
    }

    @PropertyName("name")
    public String getName() {
        return name;
    }

    @PropertyName("name")
    public void setName(String name) {
        this.name = name;
    }

    @PropertyName("icon")
    public String getIcon() {
        return icon;
    }

    @PropertyName("icon")
    public void setIcon(String icon) {
        this.icon = icon;
    }

    @PropertyName("color")
    public String getColor() {
        return color;
    }

    @PropertyName("color")
    public void setColor(String color) {
        this.color = color;
    }

    @PropertyName("type")
    public String getType() {
        return type;
    }

    @PropertyName("type")
    public void setType(String type) {
        this.type = type;
    }

    @PropertyName("isDefault")
    public boolean getIsDefault() {
        return isDefault;
    }

    @PropertyName("isDefault")
    public void setIsDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    @PropertyName("archived")
    public boolean getArchived() {
        return archived;
    }

    @PropertyName("archived")
    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    @PropertyName("bucket")
    public String getBucket() {
        return bucket;
    }

    @PropertyName("bucket")
    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public static Category fromDocument(DocumentSnapshot doc) {
        Category c = new Category();
        c.setId(doc.getString("id"));
        c.setName(doc.getString("name"));
        c.setIcon(doc.getString("icon"));
        c.setColor(doc.getString("color"));
        c.setType(doc.getString("type"));
        Boolean isDef = doc.getBoolean("isDefault");
        c.setIsDefault(isDef != null ? isDef : false);
        Boolean arch = doc.getBoolean("archived");
        c.setArchived(arch != null ? arch : false);
        c.setBucket(doc.getString("bucket"));
        return c;
    }
}
