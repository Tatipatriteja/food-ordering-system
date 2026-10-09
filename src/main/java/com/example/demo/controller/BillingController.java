package com.example.demo.controller;

import com.example.demo.service.BillingCalculator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Publishes the pricing rules so the browser's bill preview always matches the server. */
@RestController
@RequestMapping("/api/billing")
public class BillingController {
    private final BillingCalculator billing;

    public BillingController(BillingCalculator billing) { this.billing = billing; }

    @GetMapping("/rules")
    public BillingCalculator.Rules rules() { return billing.rules(); }
}
