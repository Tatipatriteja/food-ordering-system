package com.example.demo.service;

import com.example.demo.dto.OrderRequest;
import com.example.demo.dto.OrderResponse;
import com.example.demo.entity.FoodItem;
import com.example.demo.entity.FoodOrder;
import com.example.demo.entity.OrderItem;
import com.example.demo.entity.OrderStatus;
import com.example.demo.exception.InvalidStatusTransitionException;
import com.example.demo.exception.ItemUnavailableException;
import com.example.demo.exception.NotFoundException;
import com.example.demo.repository.FoodOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Order workflow: placing orders, reading them and moving them through their life cycle. */
@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /** Upper limit per dish in one order (also enforced per line by the request validation). */
    static final int MAX_QUANTITY_PER_ITEM = 50;

    private final FoodOrderRepository orders;
    private final FoodService foods;
    private final BillingCalculator billing;
    private final Clock clock;

    public OrderService(FoodOrderRepository orders, FoodService foods, BillingCalculator billing, Clock clock) {
        this.orders = orders;
        this.foods = foods;
        this.billing = billing;
        this.clock = clock;
    }

    /**
     * Places an order: merges repeated dishes, checks availability, snapshots names and prices,
     * and lets {@link BillingCalculator} produce the bill. All or nothing (single transaction).
     */
    @Transactional
    public OrderResponse place(OrderRequest request) {
        Map<Long, Integer> quantities = mergeLines(request.items());

        FoodOrder order = new FoodOrder();
        order.setCustomerName(request.customerName().trim());
        order.setCustomerPhone(normalisePhone(request.customerPhone()));
        order.setOrderTime(LocalDateTime.now(clock));

        BigDecimal subtotal = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> line : quantities.entrySet()) {
            FoodItem food = foods.getEntity(line.getKey());
            if (!food.isAvailable()) {
                throw new ItemUnavailableException(food.getName());
            }
            OrderItem item = new OrderItem(food.getId(), food.getName(), food.getPrice(), line.getValue());
            order.addItem(item);
            subtotal = subtotal.add(item.getLineTotal());
        }

        BillingCalculator.Bill bill = billing.calculate(subtotal);
        order.setSubtotal(bill.subtotal());
        order.setTax(bill.tax());
        order.setDiscount(bill.discount());
        order.setTotal(bill.total());

        FoodOrder saved = orders.save(order);
        log.info("Order {} placed: {} line(s), total {}", saved.getId(), saved.getItems().size(), saved.getTotal());
        return OrderResponse.from(saved);
    }

    /** All orders, newest first; optionally only those with the given status. */
    @Transactional(readOnly = true)
    public List<OrderResponse> list(OrderStatus status) {
        List<FoodOrder> found = status == null
                ? orders.findAllByOrderByOrderTimeDescIdDesc()
                : orders.findByStatusOrderByOrderTimeDescIdDesc(status);
        return found.stream().map(OrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id) {
        return OrderResponse.from(find(id));
    }

    @Transactional
    public OrderResponse updateStatus(Long id, OrderStatus next) {
        FoodOrder order = find(id);
        OrderStatus current = order.getStatus();
        if (!current.canTransitionTo(next)) {
            throw new InvalidStatusTransitionException(current, next);
        }
        order.setStatus(next);
        FoodOrder saved = orders.save(order);
        log.info("Order {} moved {} -> {}", id, current, next);
        return OrderResponse.from(saved);
    }

    private FoodOrder find(Long id) {
        return orders.findById(id).orElseThrow(() -> new NotFoundException("Order " + id + " not found"));
    }

    /** Adds up repeated dishes ("2 x pizza" + "1 x pizza" = "3 x pizza") and enforces the per-dish limit. */
    private static Map<Long, Integer> mergeLines(List<OrderRequest.Line> lines) {
        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (OrderRequest.Line line : lines) {
            merged.merge(line.foodId(), line.quantity(), Integer::sum);
        }
        merged.forEach((foodId, quantity) -> {
            if (quantity > MAX_QUANTITY_PER_ITEM) {
                throw new IllegalArgumentException(
                        "Quantity for item " + foodId + " cannot exceed " + MAX_QUANTITY_PER_ITEM);
            }
        });
        return merged;
    }

    private static String normalisePhone(String phone) {
        return phone == null || phone.isBlank() ? null : phone.trim();
    }
}
