package com.example.demo.exception;

/** Thrown when a customer tries to order a dish that is switched off on the menu. Maps to HTTP 409. */
public class ItemUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ItemUnavailableException(String foodName) {
        super("'" + foodName + "' is currently unavailable");
    }
}
