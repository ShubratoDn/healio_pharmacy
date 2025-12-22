package com.heal.io.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class DatabaseConfig {

    @Bean
    public CommandLineRunner updateSchema(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                // Remove the not-null constraint from product_category_id
                jdbcTemplate.execute("ALTER TABLE product ALTER COLUMN product_category_id DROP NOT NULL");
                System.out.println("Successfully updated product table: product_category_id is now nullable.");
            } catch (Exception e) {
                // If it fails, it might already be nullable or the table doesn't exist yet
                System.out.println(
                        "Note: Could not alter product table (it might already be updated): " + e.getMessage());
            }
        };
    }
}
