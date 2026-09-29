package com.requestmanagement.backend.request;

import com.requestmanagement.backend.user.User;

public record AssigneeResponse(Long id, String fullName) {
    public static AssigneeResponse from(User user) {
        return new AssigneeResponse(user.getId(), user.getFullName());
    }
}
