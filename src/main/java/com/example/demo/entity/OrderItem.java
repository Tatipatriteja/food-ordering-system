package com.example.demo.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** One line of an order. Name and price are copied from the menu at purchase time (a snapshot). */
@Entity
@Table(name = "order_items")
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long foodId;
    private String foodName;
    @Column(precision = 10, scale = 2)
    private BigDecimal price;
    private int quantity;
    @Column(precision = 10, scale = 2)
    private BigDecimal lineTotal;

    public OrderItem() {}
    public OrderItem(Long foodId, String foodName, BigDecimal price, int quantity) {
        this.foodId = foodId;
        this.foodName = foodName;
        this.price = price;
        this.quantity = quantity;
        this.lineTotal = price.multiply(BigDecimal.valueOf(quantity));
    }
    public Long getId() { return id; }
    public Long getFoodId() { return foodId; }
    public String getFoodName() { return foodName; }
    public BigDecimal getPrice() { return price; }
    public int getQuantity() { return quantity; }
    public BigDecimal getLineTotal() { return lineTotal; }
}
