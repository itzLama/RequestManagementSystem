package com.requestmanagement.backend.auth;

import com.requestmanagement.backend.user.User;

public record AuthUserResponse(
        Long userId,
        String fullName,
        User.Role role
) {
    public static AuthUserResponse from(User user) {
        return new AuthUserResponse(user.getId(), user.getFullName(), user.getRole());
    }
}
