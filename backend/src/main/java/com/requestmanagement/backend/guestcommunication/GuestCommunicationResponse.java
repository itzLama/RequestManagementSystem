package com.requestmanagement.backend.guestcommunication;

import java.time.LocalDateTime;

import com.requestmanagement.backend.user.User;

public record GuestCommunicationResponse(
        Long id,
        String message,
        String senderName,
        User.Role senderRole,
        LocalDateTime sentAt
) {
    public static GuestCommunicationResponse from(GuestCommunication communication) {
        return new GuestCommunicationResponse(
                communication.getId(),
                communication.getMessage(),
                communication.getSentBy().getFullName(),
                communication.getSentBy().getRole(),
                communication.getSentAt()
        );
    }
}
