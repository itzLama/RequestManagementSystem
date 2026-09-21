package com.requestmanagement.backend.comment;

import java.time.LocalDateTime;
import com.requestmanagement.backend.user.User;

public record CommentResponse(Long id, String text, String authorName,
                              User.Role authorRole, LocalDateTime createdAt) {
    public static CommentResponse from(Comment comment) {
        return new CommentResponse(comment.getId(), comment.getCommentText(),
                comment.getUser().getFullName(), comment.getUser().getRole(), comment.getCreatedAt());
    }
}
