package com.example.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure pricing rules, configurable from application.properties and easy to unit test.
 *
 * <ul>
 *   <li>Tax = subtotal x taxRate (rounded half-up to 2 decimals).</li>
 *   <li>Discount = subtotal x discountRate, only when subtotal >= discountThreshold.</li>
 *   <li>Total = subtotal + tax - discount.</li>
 * </ul>
 */
@Component
public class BillingCalculator {
    private final BigDecimal taxRate;
    private final BigDecimal discountThreshold;
    private final BigDecimal discountRate;

    public BillingCalculator(@Value("${app.billing.tax-rate:0.05}") BigDecimal taxRate,
                             @Value("${app.billing.discount-threshold:500}") BigDecimal discountThreshold,
                             @Value("${app.billing.discount-rate:0.10}") BigDecimal discountRate) {
        this.taxRate = requireRate(taxRate, "taxRate");
        this.discountThreshold = requireNonNegative(discountThreshold, "discountThreshold");
        this.discountRate = requireRate(discountRate, "discountRate");
    }

    private static BigDecimal requireRate(BigDecimal rate, String name) {
        if (rate == null || rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
        return rate;
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(name + " must be zero or greater");
        }
        return value;
    }

    public Bill calculate(BigDecimal subtotal) {
        requireNonNegative(subtotal, "subtotal");
        BigDecimal tax = subtotal.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discount = subtotal.compareTo(discountThreshold) >= 0
                ? subtotal.multiply(discountRate).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);
        BigDecimal total = subtotal.add(tax).subtract(discount).setScale(2, RoundingMode.HALF_UP);
        return new Bill(subtotal.setScale(2, RoundingMode.HALF_UP), tax, discount, total);
    }

    /** The configured rules, so the front end can show the same numbers instead of hard-coding them. */
    public Rules rules() {
        return new Rules(taxRate, discountThreshold, discountRate);
    }

    public record Bill(BigDecimal subtotal, BigDecimal tax, BigDecimal discount, BigDecimal total) {}

    public record Rules(BigDecimal taxRate, BigDecimal discountThreshold, BigDecimal discountRate) {}
}
