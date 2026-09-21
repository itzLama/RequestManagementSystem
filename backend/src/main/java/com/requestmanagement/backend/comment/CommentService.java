package com.requestmanagement.backend.comment;

import com.requestmanagement.backend.request.Request;
import com.requestmanagement.backend.request.RequestRepository;
import com.requestmanagement.backend.user.User;
import com.requestmanagement.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CommentService {
    private final CommentRepository commentRepository;
    private final RequestRepository requestRepository;
    private final UserRepository userRepository;

    @Transactional
    public CommentResponse add(Long requestId, Long authorId, AddCommentRequest input) {
        Request request = requestRepository.findByIdAndCreatedBy_Id(requestId, authorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found."));
        String text = input.text().trim();
        if (text.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment is required.");
        }
        if (text.length() > 10000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment must be at most 10000 characters.");
        }
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required."));
        return CommentResponse.from(commentRepository.save(Comment.create(request, author, text)));
    }
}
