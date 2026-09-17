package com.requestmanagement.backend.util;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        hashPasswordIfNeeded("sara.saad@example.com", "Password123");
        hashPasswordIfNeeded("nora.ahmed@example.com", "Password123");
        hashPasswordIfNeeded("nouf.khaled@example.com", "Password123");
    }

    private void hashPasswordIfNeeded(String email, String rawPassword) {

        String currentPassword = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE email = ?",
                String.class,
                email
        );

        if (currentPassword != null && !currentPassword.startsWith("$2")) {

            String hashedPassword = passwordEncoder.encode(rawPassword);

            jdbcTemplate.update(
                    "UPDATE users SET password_hash = ? WHERE email = ?",
                    hashedPassword,
                    email
            );
        }
    }
}
