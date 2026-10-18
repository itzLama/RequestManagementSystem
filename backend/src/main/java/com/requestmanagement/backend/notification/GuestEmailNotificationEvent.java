package com.requestmanagement.backend.notification;

import com.requestmanagement.backend.guestcommunication.GuestCommunication;
import com.requestmanagement.backend.request.Request;
import lombok.Getter;

import java.util.Objects;

@Getter
public final class GuestEmailNotificationEvent {
    private final GuestEmailNotificationCategory category;
    private final String recipientEmail;
    private final Long requestId;
    private final String guestName;
    private final String status;
    private final String message;

    private GuestEmailNotificationEvent(
            GuestEmailNotificationCategory category,
            String recipientEmail,
            Long requestId,
            String guestName,
            String status,
            String message
    ) {
        this.category = category;
        this.recipientEmail = recipientEmail;
        this.requestId = requestId;
        this.guestName = guestName;
        this.status = status;
        this.message = message;
    }

    public static GuestEmailNotificationEvent statusChanged(Request request) {
        validateGuestRequest(request);
        return new GuestEmailNotificationEvent(
                GuestEmailNotificationCategory.REQUEST_STATUS_CHANGED,
                request.getGuestEmail(),
                request.getId(),
                request.getGuestName(),
                request.statusDisplayName(),
                null);
    }

    public static GuestEmailNotificationEvent messageSent(GuestCommunication communication) {
        Objects.requireNonNull(communication, "communication is required");
        Request request = communication.getRequest();
        validateGuestRequest(request);

        String message = Objects.requireNonNull(
                communication.getMessage(), "communication message is required");
        if (message.isBlank()) {
            throw new IllegalArgumentException("communication message must not be blank");
        }

        return new GuestEmailNotificationEvent(
                GuestEmailNotificationCategory.MESSAGE_SENT,
                request.getGuestEmail(),
                request.getId(),
                request.getGuestName(),
                null,
                message);
    }

    private static void validateGuestRequest(Request request) {
        Objects.requireNonNull(request, "request is required");
        if (!request.isGuest()) {
            throw new IllegalArgumentException("email notifications require a Guest Request");
        }
        Objects.requireNonNull(request.getId(), "persisted request ID is required");

        String guestEmail = Objects.requireNonNull(request.getGuestEmail(), "guest email is required");
        String guestName = Objects.requireNonNull(request.getGuestName(), "guest name is required");
        if (guestEmail.isBlank()) {
            throw new IllegalArgumentException("guest email must not be blank");
        }
        if (guestName.isBlank()) {
            throw new IllegalArgumentException("guest name must not be blank");
        }
    }
}
