package com.requestmanagement.backend.guestcommunication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendGuestCommunicationRequest(
        @NotBlank(message = "Message is required.")
        @Size(max = 10000, message = "Message must be at most 10000 characters.")
        String message
) {
}
