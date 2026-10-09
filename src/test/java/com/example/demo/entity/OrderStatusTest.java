package com.example.demo.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderStatusTest {

    @ParameterizedTest(name = "{0} -> {1} allowed: {2}")
    @CsvSource({
            "PLACED,    PREPARING, true",
            "PLACED,    CANCELLED, true",
            "PLACED,    READY,     false",
            "PLACED,    DELIVERED, false",
            "PLACED,    PLACED,    false",
            "PREPARING, READY,     true",
            "PREPARING, CANCELLED, true",
            "PREPARING, PLACED,    false",
            "READY,     DELIVERED, true",
            "READY,     CANCELLED, true",
            "READY,     PREPARING, false",
            "DELIVERED, CANCELLED, false",
            "DELIVERED, PLACED,    false",
            "CANCELLED, PLACED,    false",
            "CANCELLED, DELIVERED, false"
    })
    void transitionRules(OrderStatus from, OrderStatus to, boolean allowed) {
        assertEquals(allowed, from.canTransitionTo(to));
    }

    @Test
    void nextStatusesListsOnlyAllowedMoves() {
        assertEquals(List.of(OrderStatus.PREPARING, OrderStatus.CANCELLED), OrderStatus.PLACED.nextStatuses());
        assertEquals(List.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED), OrderStatus.READY.nextStatuses());
    }

    @Test
    void deliveredAndCancelledAreTerminal() {
        assertTrue(OrderStatus.DELIVERED.isTerminal());
        assertTrue(OrderStatus.CANCELLED.isTerminal());
        assertFalse(OrderStatus.PLACED.isTerminal());
    }
}
