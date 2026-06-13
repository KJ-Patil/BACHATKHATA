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

    public static Category fromDocument(DocumentSnapshot doc) {
        Category c = new Category();
        c.setId(doc.getString("id"));
        c.setName(doc.getString("name"));
        c.setIcon(doc.getString("icon"));
        c.setColor(doc.getString("color"));
        c.setType(doc.getString("type"));
        Boolean isDef = doc.getBoolean("isDefault");
        c.setIsDefault(isDef != null ? isDef : false);
        return c;
    }
}
