package com.requestmanagement.backend.guest;

import com.requestmanagement.backend.request.RequestPriority;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import com.requestmanagement.backend.guestform.GuestFormDtos.AnswerInput;
import java.util.List;

public record CreateGuestProjectRequest(
        @NotBlank(message = "Guest name is required.") @Size(max = 150, message = "Guest name must be at most 150 characters.") String guestName,
        @NotBlank(message = "Guest email is required.") @Email(message = "Guest email must be valid.") @Size(max = 255, message = "Guest email must be at most 255 characters.") String guestEmail,
        @NotBlank(message = "Title is required.") @Size(max = 200, message = "Title must be at most 200 characters.") String title,
        @Size(max = 10000, message = "Description must be at most 10000 characters.") String description,
        @Positive(message = "Work Type ID must be positive.") Long workTypeId,
        RequestPriority priority,
        @Size(max = 30, message = "At most 30 custom field answers are allowed.") List<@Valid AnswerInput> answers
) {
    public CreateGuestProjectRequest {
        guestName = trim(guestName);
        guestEmail = trim(guestEmail);
        title = trim(title);
        description = trim(description);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
