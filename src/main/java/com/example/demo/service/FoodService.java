package com.example.demo.service;

import com.example.demo.dto.FoodRequest;
import com.example.demo.dto.FoodResponse;
import com.example.demo.entity.FoodItem;
import com.example.demo.exception.NotFoundException;
import com.example.demo.repository.FoodItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Menu management: listing, creating, updating and deleting dishes. */
@Service
@Transactional(readOnly = true)
public class FoodService {
    private static final Logger log = LoggerFactory.getLogger(FoodService.class);

    private final FoodItemRepository repository;

    public FoodService(FoodItemRepository repository) { this.repository = repository; }

    public List<FoodResponse> list(boolean onlyAvailable) {
        List<FoodItem> items = onlyAvailable ? repository.findByAvailableTrue() : repository.findAll();
        return items.stream().map(FoodResponse::from).toList();
    }

    public FoodResponse get(Long id) {
        return FoodResponse.from(getEntity(id));
    }

    /** Used by OrderService, which needs the entity itself (price, availability). */
    public FoodItem getEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Food item " + id + " not found"));
    }

    @Transactional
    public FoodResponse create(FoodRequest request) {
        FoodItem item = new FoodItem();
        apply(item, request);
        FoodItem saved = repository.save(item);
        log.info("Created menu item {} ('{}')", saved.getId(), saved.getName());
        return FoodResponse.from(saved);
    }

    @Transactional
    public FoodResponse update(Long id, FoodRequest request) {
        FoodItem item = getEntity(id);
        apply(item, request);
        FoodItem saved = repository.save(item);
        log.info("Updated menu item {}", id);
        return FoodResponse.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        FoodItem item = getEntity(id); // 404 if missing
        repository.delete(item);
        log.info("Deleted menu item {}", id);
    }

    private static void apply(FoodItem item, FoodRequest request) {
        item.setName(request.name().trim());
        item.setCategory(blankToNull(request.category()));
        item.setDescription(blankToNull(request.description()));
        item.setPrice(request.price());
        item.setAvailable(request.availableOrDefault());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
