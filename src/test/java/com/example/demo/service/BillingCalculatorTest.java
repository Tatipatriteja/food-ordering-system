package com.example.demo.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BillingCalculatorTest {
    private final BillingCalculator calc =
            new BillingCalculator(new BigDecimal("0.05"), new BigDecimal("500"), new BigDecimal("0.10"));

    @Test
    void noDiscountBelowThreshold() {
        BillingCalculator.Bill bill = calc.calculate(new BigDecimal("200"));
        assertEquals(new BigDecimal("10.00"), bill.tax());
        assertEquals(new BigDecimal("0.00"), bill.discount());
        assertEquals(new BigDecimal("210.00"), bill.total());
    }

    @Test
    void discountAppliesAtThreshold() {
        BillingCalculator.Bill bill = calc.calculate(new BigDecimal("500"));
        assertEquals(new BigDecimal("25.00"), bill.tax());
        assertEquals(new BigDecimal("50.00"), bill.discount());
        assertEquals(new BigDecimal("475.00"), bill.total());
    }

    @Test
    void zeroSubtotalProducesZeroBill() {
        BillingCalculator.Bill bill = calc.calculate(BigDecimal.ZERO);
        assertEquals(new BigDecimal("0.00"), bill.subtotal());
        assertEquals(new BigDecimal("0.00"), bill.tax());
        assertEquals(new BigDecimal("0.00"), bill.discount());
        assertEquals(new BigDecimal("0.00"), bill.total());
    }

    @Test
    void rejectsNegativeSubtotal() {
        assertThrows(IllegalArgumentException.class, () -> calc.calculate(new BigDecimal("-0.01")));
    }

    @Test
    void rejectsNullSubtotal() {
        assertThrows(IllegalArgumentException.class, () -> calc.calculate(null));
    }

    @Test
    void rejectsInvalidTaxRate() {
        assertThrows(IllegalArgumentException.class,
                () -> new BillingCalculator(new BigDecimal("1.01"), new BigDecimal("500"), new BigDecimal("0.10")));
    }

    @Test
    void roundsToTwoDecimals() {
        BillingCalculator.Bill bill = calc.calculate(new BigDecimal("99.99"));
        assertEquals(new BigDecimal("5.00"), bill.tax());
        assertEquals(new BigDecimal("104.99"), bill.total());
    }

    @Test
    void justBelowThresholdHasNoDiscount() {
        BillingCalculator.Bill bill = calc.calculate(new BigDecimal("499.99"));
        assertEquals(new BigDecimal("0.00"), bill.discount());
    }

    @ParameterizedTest(name = "subtotal {0} -> tax {1}, discount {2}, total {3}")
    @CsvSource({
            "0,      0.00,   0.00,   0.00",
            "100,    5.00,   0.00,   105.00",
            "499.99, 25.00,  0.00,   524.99",
            "500,    25.00,  50.00,  475.00",
            "1000,   50.00,  100.00, 950.00",
            "1234.56, 61.73, 123.46, 1172.83"
    })
    void billTable(String subtotal, String tax, String discount, String total) {
        BillingCalculator.Bill bill = calc.calculate(new BigDecimal(subtotal));
        assertEquals(new BigDecimal(tax), bill.tax());
        assertEquals(new BigDecimal(discount), bill.discount());
        assertEquals(new BigDecimal(total), bill.total());
    }

    @Test
    void rulesExposeTheConfiguration() {
        BillingCalculator.Rules rules = calc.rules();
        assertEquals(new BigDecimal("0.05"), rules.taxRate());
        assertEquals(new BigDecimal("500"), rules.discountThreshold());
        assertEquals(new BigDecimal("0.10"), rules.discountRate());
    }

    @Test
    void totalNeverNegativeEvenWithFullDiscount() {
        BillingCalculator everythingFree = new BillingCalculator(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE);
        assertTrue(everythingFree.calculate(new BigDecimal("80")).total().signum() >= 0);
    }
}
