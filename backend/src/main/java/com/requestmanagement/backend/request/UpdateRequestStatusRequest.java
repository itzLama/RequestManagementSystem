package com.requestmanagement.backend.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateRequestStatusRequest(
        @NotNull(message = "Status is required.")
        RequestStatus status,

        @Size(max = 10000, message = "Change note must be at most 10000 characters.")
        String changeNote
) {
}
