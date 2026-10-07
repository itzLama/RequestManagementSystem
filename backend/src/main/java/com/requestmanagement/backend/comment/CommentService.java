package com.requestmanagement.backend.comment;

import com.requestmanagement.backend.request.Request;
import com.requestmanagement.backend.request.RequestRepository;
import com.requestmanagement.backend.project.ProjectAccessService;
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
    private final ProjectAccessService projectAccessService;

    @Transactional
    public CommentResponse add(Long requestId, Long authorId, AddCommentRequest input) {
        Request request = requestRepository.findByIdAndCreatedBy_IdAndProjectIsNull(requestId, authorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found."));
        return saveEmployeeComment(request, requireActiveEmployee(authorId), input);
    }

    @Transactional
    public CommentResponse addAssigned(Long requestId, Long authorId, AddCommentRequest input) {
        Request request = requestRepository.findByIdAndAssignedTo_IdAndProjectIsNull(requestId, authorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found."));
        return saveEmployeeComment(request, requireActiveEmployee(authorId), input);
    }

    @Transactional
    public CommentResponse addProject(Long projectId, Long requestId, Long authorId, AddCommentRequest input) {
        projectAccessService.requireMutableProject(projectId, authorId);
        Request request = requestRepository.findByIdAndProject_Id(requestId, projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found."));
        return saveEmployeeComment(request, projectAccessService.requireActiveEmployee(authorId), input);
    }

    @Transactional
    public CommentResponse addAdmin(Long requestId, Long authorId, AddCommentRequest input, boolean internal) {
        Request request = requestRepository.findAdminDetailsByIdAndProjectIsNull(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found."));
        return saveAdminComment(request, authorId, input, internal);
    }

    @Transactional
    public CommentResponse addAdminProject(Long projectId, Long requestId, Long authorId,
                                           AddCommentRequest input, boolean internal) {
        Request request = requestRepository.findByIdAndProject_Id(requestId, projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found."));
        projectAccessService.requireActive(request.getProject());
        return saveAdminComment(request, authorId, input, internal);
    }

    private CommentResponse saveAdminComment(Request request, Long authorId,
                                             AddCommentRequest input, boolean internal) {
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

    private CommentResponse saveEmployeeComment(Request request, User author, AddCommentRequest input) {
        return CommentResponse.from(commentRepository.save(
                Comment.create(request, author, validateText(input))));
    }

    private User requireActiveEmployee(Long userId) {
        return userRepository.findByIdAndRole(userId, User.Role.EMPLOYEE)
                .filter(User::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Active Employee access is required."));
    }
}
