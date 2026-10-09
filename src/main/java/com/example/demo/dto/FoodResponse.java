package com.example.demo.dto;

import com.example.demo.entity.FoodItem;

import java.math.BigDecimal;

/** What the API returns for a menu item (the entity itself is never exposed). */
public record FoodResponse(Long id, String name, String category, String description,
                           BigDecimal price, boolean available) {

    public static FoodResponse from(FoodItem f) {
        return new FoodResponse(f.getId(), f.getName(), f.getCategory(), f.getDescription(),
                f.getPrice(), f.isAvailable());
    }
}
