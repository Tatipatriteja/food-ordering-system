package com.example.demo.controller;

import com.example.demo.dto.OrderRequest;
import com.example.demo.dto.OrderResponse;
import com.example.demo.dto.StatusRequest;
import com.example.demo.entity.OrderStatus;
import com.example.demo.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** REST endpoints for orders. */
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody OrderRequest request) { return service.place(request); }

    /** GET /api/orders?status=PLACED filters by status; without it all orders are returned, newest first. */
    @GetMapping
    public List<OrderResponse> all(@RequestParam(required = false) OrderStatus status) {
        return service.list(status);
    }

    @GetMapping("/{id}")
    public OrderResponse one(@PathVariable Long id) { return service.get(id); }

    @PutMapping("/{id}/status")
    public OrderResponse status(@PathVariable Long id, @Valid @RequestBody StatusRequest body) {
        return service.updateStatus(id, body.status());
    }
}
