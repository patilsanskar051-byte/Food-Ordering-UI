package com.crave.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Represents a food item on the Crave menu.
 * Uses a fixed ID (not auto-generated) to match the frontend's existing food IDs.
 */
@Entity
@Table(name = "foods")
public class Food {

    @Id
    private Long id;

    private String name;
    private String category;
    private String description;
    private int price;
    private String rating;
    private String badge;
    private String image;

    @jakarta.persistence.Transient
    private boolean available = true;

    public Food() {
        this.available = true;
    }

    public Food(Long id, String name, String category, String description,
                int price, String rating, String badge, String image) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.description = description;
        this.price = price;
        this.rating = rating;
        this.badge = badge;
        this.image = image;
        this.available = true;
    }

    public Food(Long id, String name, String category, String description,
                int price, String rating, String badge, String image, boolean available) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.description = description;
        this.price = price;
        this.rating = rating;
        this.badge = badge;
        this.image = image;
        this.available = available;
    }

    // --- Getters and Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public String getRating() {
        return rating;
    }

    public void setRating(String rating) {
        this.rating = rating;
    }

    public String getBadge() {
        return badge;
    }

    public void setBadge(String badge) {
        this.badge = badge;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean getAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }
}
