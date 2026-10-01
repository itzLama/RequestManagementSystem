package com.requestmanagement.backend.user;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    public enum Role {
        ADMIN,
        EMPLOYEE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static User createEmployee(String fullName, String email, String passwordHash) {
        User employee = new User();
        LocalDateTime now = LocalDateTime.now();
        employee.fullName = fullName;
        employee.email = email;
        employee.passwordHash = passwordHash;
        employee.role = Role.EMPLOYEE;
        employee.active = true;
        employee.createdAt = now;
        employee.updatedAt = now;
        return employee;
    }

    public void updateEmployeeProfile(String fullName, String email) {
        this.fullName = fullName;
        this.email = email;
        this.updatedAt = LocalDateTime.now();
    }

    public void deactivate() {
        if (active) {
            active = false;
            updatedAt = LocalDateTime.now();
        }
    }

}
