package com.requestmanagement.backend.attachment;

import jakarta.validation.constraints.NotNull;

public record GuestVisibilityRequest(@NotNull Boolean guestVisible) { }
