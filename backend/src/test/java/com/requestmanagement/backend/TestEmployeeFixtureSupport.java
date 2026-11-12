package com.requestmanagement.backend;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

public final class TestEmployeeFixtureSupport {
    private TestEmployeeFixtureSupport() {
    }

    public static EmployeeFixture createActiveEmployee(JdbcTemplate jdbc, String fullName) {
        String email = "test-employee-" + UUID.randomUUID() + "@example.com";
        long id = jdbc.queryForObject("""
                INSERT INTO users(full_name,email,password_hash,role,is_active)
                VALUES (?, ?,
                        (SELECT password_hash FROM users WHERE email='nora.ahmed@example.com'),
                        'EMPLOYEE', true)
                RETURNING user_id
                """, Long.class, fullName, email);
        return new EmployeeFixture(id, email);
    }

    public record EmployeeFixture(long id, String email) {
    }
}
