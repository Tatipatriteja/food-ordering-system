package com.example.demo.dto;

import com.example.demo.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record StatusRequest(@NotNull OrderStatus status) {}
