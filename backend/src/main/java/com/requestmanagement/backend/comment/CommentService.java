package com.requestmanagement.backend.comment;

import com.requestmanagement.backend.request.Request;
import com.requestmanagement.backend.request.RequestRepository;
import com.requestmanagement.backend.request.RequestStatus;
import com.requestmanagement.backend.statushistory.StatusHistory;
import com.requestmanagement.backend.statushistory.StatusHistoryRepository;
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
    private final StatusHistoryRepository statusHistoryRepository;

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
                .filter(User::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "This account is inactive."));
        CommentResponse response = CommentResponse.from(commentRepository.save(Comment.create(request, author, text)));
        RequestStatus previousStatus = request.getStatus();
        if (previousStatus == RequestStatus.COMPLETED || previousStatus == RequestStatus.REJECTED) {
            request.updateWorkflow(RequestStatus.IN_PROGRESS, request.getAssignedTo());
            requestRepository.saveAndFlush(request);
            statusHistoryRepository.saveAndFlush(StatusHistory.create(
                    request,
                    previousStatus,
                    RequestStatus.IN_PROGRESS,
                    author,
                    "Reopened after requester comment"
            ));
        }
        return response;
    }

    @Transactional
    public CommentResponse addAdmin(Long requestId, Long authorId, AddCommentRequest input, boolean internal) {
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found."));
        String text = validateText(input);
        User author = userRepository.findById(authorId)
                .filter(User::isActive)
                .filter(user -> user.getRole() == User.Role.ADMIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access is required."));
        return CommentResponse.from(commentRepository.save(Comment.create(request, author, text, internal)));
    }

    private String validateText(AddCommentRequest input) {
        String text = input.text().trim();
        if (text.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment is required.");
        }
        if (text.length() > 10000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment must be at most 10000 characters.");
        }
        return text;
    }
}
