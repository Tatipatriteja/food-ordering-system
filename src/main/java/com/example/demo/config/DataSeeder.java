package com.example.demo.config;

import com.example.demo.entity.FoodItem;
import com.example.demo.repository.FoodItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;

/** Fills an empty menu with sample dishes so the app is usable on first run. */
@Configuration
public class DataSeeder {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Bean
    CommandLineRunner seedMenu(FoodItemRepository repo) {
        return args -> {
            if (repo.count() > 0) return;
            log.info("Menu is empty - seeding sample dishes");
            repo.saveAll(List.of(
                    food("Margherita Pizza", "Pizza", "Tomato, mozzarella and fresh basil", "249"),
                    food("Paneer Tikka Pizza", "Pizza", "Smoky paneer, onion and capsicum", "329"),
                    food("Veg Burger", "Burgers", "Crispy patty with lettuce and house sauce", "129"),
                    food("Chicken Burger", "Burgers", "Juicy grilled chicken with cheese", "169"),
                    food("Butter Chicken", "Mains", "Creamy tomato gravy, served with naan", "349"),
                    food("Paneer Butter Masala", "Mains", "Rich cottage cheese curry", "289"),
                    food("Veg Biryani", "Mains", "Fragrant basmati rice with vegetables", "219"),
                    food("French Fries", "Sides", "Golden, salted and crisp", "89"),
                    food("Garlic Bread", "Sides", "Toasted with garlic butter", "99"),
                    food("Cold Coffee", "Drinks", "Chilled and creamy", "109"),
                    food("Fresh Lime Soda", "Drinks", "Sweet or salted", "69"),
                    food("Chocolate Brownie", "Desserts", "Warm brownie, fudge drizzle", "119")));
        };
    }

    private static FoodItem food(String name, String category, String description, String price) {
        return new FoodItem(name, category, description, new BigDecimal(price));
    }
}
