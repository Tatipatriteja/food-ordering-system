package com.example.demo.dto;

import com.example.demo.entity.FoodOrder;
import com.example.demo.entity.OrderItem;
import com.example.demo.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * What the API returns for an order. {@code nextStatuses} lists the allowed status changes so the
 * front end never has to duplicate the life-cycle rules.
 */
public record OrderResponse(Long id, String customerName, String customerPhone,
                            BigDecimal subtotal, BigDecimal tax, BigDecimal discount, BigDecimal total,
                            OrderStatus status, LocalDateTime orderTime,
                            List<ItemResponse> items, List<OrderStatus> nextStatuses) {

    public record ItemResponse(Long id, Long foodId, String foodName, BigDecimal price,
                               int quantity, BigDecimal lineTotal) {
        static ItemResponse from(OrderItem i) {
            return new ItemResponse(i.getId(), i.getFoodId(), i.getFoodName(), i.getPrice(),
                    i.getQuantity(), i.getLineTotal());
        }
    }

    public static OrderResponse from(FoodOrder o) {
        return new OrderResponse(o.getId(), o.getCustomerName(), o.getCustomerPhone(),
                o.getSubtotal(), o.getTax(), o.getDiscount(), o.getTotal(),
                o.getStatus(), o.getOrderTime(),
                o.getItems().stream().map(ItemResponse::from).toList(),
                o.getStatus().nextStatuses());
    }
}
