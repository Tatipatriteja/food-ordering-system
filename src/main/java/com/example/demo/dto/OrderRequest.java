package com.example.demo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record OrderRequest(
        @NotBlank @Size(max = 100) String customerName,
        @Pattern(regexp = "^$|^[0-9+\\-\\s()]{7,20}$", message = "must be a valid phone number") String customerPhone,
        @NotEmpty @Valid List<Line> items) {

    public record Line(
            @NotNull Long foodId,
            @Min(1) @Max(50) int quantity) {}
}
