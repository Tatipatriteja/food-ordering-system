package com.example.demo.exception;

import com.example.demo.entity.OrderStatus;

/** Thrown when an order is asked to jump to a status its life cycle does not allow. Maps to HTTP 409. */
public class InvalidStatusTransitionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InvalidStatusTransitionException(OrderStatus from, OrderStatus to) {
        super("Cannot change an order from " + from + " to " + to);
    }
}
