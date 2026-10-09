package com.example.demo.entity;

import java.util.Arrays;
import java.util.List;

/**
 * Life cycle of an order.
 *
 * <pre>
 * PLACED -> PREPARING -> READY -> DELIVERED
 *    \          \          \
 *     +----------+----------+--> CANCELLED
 * </pre>
 *
 * DELIVERED and CANCELLED are terminal: nothing can follow them.
 */
public enum OrderStatus {
    PLACED, PREPARING, READY, DELIVERED, CANCELLED;

    /** Returns true if an order currently in this status may move to {@code next}. */
    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case PLACED -> next == PREPARING || next == CANCELLED;
            case PREPARING -> next == READY || next == CANCELLED;
            case READY -> next == DELIVERED || next == CANCELLED;
            case DELIVERED, CANCELLED -> false;
        };
    }

    /** All statuses reachable from this one (empty for terminal statuses). */
    public List<OrderStatus> nextStatuses() {
        return Arrays.stream(values()).filter(this::canTransitionTo).toList();
    }

    public boolean isTerminal() {
        return nextStatuses().isEmpty();
    }
}
