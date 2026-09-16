package com.requestmanagement.backend.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordHashGenerator {

    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        String password = "Password123";
        String hashedPassword = encoder.encode(password);

        System.out.println("Password: " + password);
        System.out.println("Hashed Password: " + hashedPassword);
    }
}