package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Shared infrastructure beans. */
@Configuration
public class AppConfig {

    /** Injecting a Clock (instead of calling LocalDateTime.now()) lets tests freeze time. */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
