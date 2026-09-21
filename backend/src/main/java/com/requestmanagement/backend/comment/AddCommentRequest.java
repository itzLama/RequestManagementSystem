package com.requestmanagement.backend.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddCommentRequest(
        @NotBlank(message = "Comment is required.")
        @Size(max = 10000, message = "Comment must be at most 10000 characters.")
        String text
) {
}
