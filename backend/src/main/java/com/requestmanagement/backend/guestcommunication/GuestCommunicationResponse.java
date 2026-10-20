package com.requestmanagement.backend.guestcommunication;

import java.time.LocalDateTime;

import com.requestmanagement.backend.user.User;

public record GuestCommunicationResponse(
        Long id,
        String message,
        String senderName,
        User.Role senderRole,
        GuestCommunication.SenderType senderType,
        LocalDateTime sentAt
) {
    public static GuestCommunicationResponse from(GuestCommunication communication) {
        String senderName = communication.getSenderName();
        if (senderName == null && communication.getSentBy() != null) {
            senderName = communication.getSentBy().getFullName();
        }
        return new GuestCommunicationResponse(
                communication.getId(),
                communication.getMessage(),
                senderName,
                communication.getSentBy() == null ? null : communication.getSentBy().getRole(),
                communication.getSenderType(),
                communication.getSentAt()
        );
    }
}
