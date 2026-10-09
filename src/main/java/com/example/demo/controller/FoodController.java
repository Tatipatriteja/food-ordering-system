package com.example.demo.controller;

import com.example.demo.dto.FoodRequest;
import com.example.demo.dto.FoodResponse;
import com.example.demo.service.FoodService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** REST endpoints for the menu. Controllers only translate HTTP to service calls. */
@RestController
@RequestMapping("/api/foods")
public class FoodController {
    private final FoodService service;

    public FoodController(FoodService service) { this.service = service; }

    /** GET /api/foods?available=true returns only orderable items. */
    @GetMapping
    public List<FoodResponse> all(@RequestParam(defaultValue = "false") boolean available) {
        return service.list(available);
    }

    @GetMapping("/{id}")
    public FoodResponse one(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FoodResponse add(@Valid @RequestBody FoodRequest request) { return service.create(request); }

    @PutMapping("/{id}")
    public FoodResponse update(@PathVariable Long id, @Valid @RequestBody FoodRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
