package com.example.demo.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/** Payload for creating or updating a menu item. {@code available} defaults to true when omitted. */
public record FoodRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 50) String category,
        @Size(max = 500) String description,
        @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal price,
        Boolean available) {

    public boolean availableOrDefault() {
        return available == null || available;
    }
}
