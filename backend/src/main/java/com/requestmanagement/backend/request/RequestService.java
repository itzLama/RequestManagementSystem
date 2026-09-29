package com.requestmanagement.backend.request;

import com.requestmanagement.backend.requesttype.RequestType;
import com.requestmanagement.backend.comment.CommentRepository;
import com.requestmanagement.backend.comment.CommentResponse;
import com.requestmanagement.backend.statushistory.StatusHistoryRepository;
import com.requestmanagement.backend.statushistory.StatusHistory;
import com.requestmanagement.backend.requesttype.RequestTypeRepository;
import com.requestmanagement.backend.user.User;
import com.requestmanagement.backend.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RequestService {

    private final RequestRepository requestRepository;
    private final RequestTypeRepository requestTypeRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final StatusHistoryRepository statusHistoryRepository;

    @Transactional(readOnly = true)
    public RequestDetailsResponse details(Long requestId, Long creatorId) {
        Request request = requestRepository.findByIdAndCreatedBy_Id(requestId, creatorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found."));
        var timeline = statusHistoryRepository.findByRequest_IdOrderByChangedAtAscIdAsc(requestId).stream()
                .map(RequestDetailsResponse.TimelineEntry::from).toList();
        var comments = commentRepository.findByRequest_IdAndInternalFalseOrderByCreatedAtAscIdAsc(requestId).stream()
                .map(CommentResponse::from).toList();
        return RequestDetailsResponse.from(request, timeline, comments);
    }

    @Transactional(readOnly = true)
    public MyRequestsPageResponse listMine(Long creatorId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<MyRequestResponse> results = requestRepository.findByCreatedBy_Id(creatorId, pageable)
                .map(MyRequestResponse::from);
        return MyRequestsPageResponse.from(results);
    }

    @Transactional(readOnly = true)
    public List<MyRequestResponse> boardMine(Long creatorId) {
        return requestRepository.findByCreatedBy_IdOrderByCreatedAtDescIdDesc(creatorId).stream()
                .map(MyRequestResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminRequestsPageResponse listAll(
            String search,
            RequestStatus status,
            Long typeId,
            RequestPriority priority,
            int page,
            int size
    ) {
        Specification<Request> filters = adminFilters(search, status, typeId, priority);
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<AdminRequestResponse> results = requestRepository
                .findAll(filters, pageable)
                .map(AdminRequestResponse::from);
        return AdminRequestsPageResponse.from(results);
    }

    @Transactional(readOnly = true)
    public List<AdminRequestResponse> boardAll(
            String search,
            RequestStatus status,
            Long typeId,
            RequestPriority priority
    ) {
        return requestRepository.findAll(
                        adminFilters(search, status, typeId, priority),
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
                ).stream()
                .map(AdminRequestResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminRequestDetailsResponse adminDetails(Long requestId) {
        Request request = findAdminRequest(requestId);
        var timeline = statusHistoryRepository.findByRequest_IdOrderByChangedAtAscIdAsc(requestId).stream()
                .map(RequestDetailsResponse.TimelineEntry::from).toList();
        var comments = commentRepository.findByRequest_IdAndInternalFalseOrderByCreatedAtAscIdAsc(requestId).stream()
                .map(CommentResponse::from).toList();
        var internalNotes = commentRepository.findByRequest_IdAndInternalTrueOrderByCreatedAtAscIdAsc(requestId).stream()
                .map(CommentResponse::from).toList();
        return AdminRequestDetailsResponse.from(request, timeline, comments, internalNotes);
    }

    @Transactional(readOnly = true)
    public List<AssigneeResponse> assignees() {
        return userRepository.findByActiveTrueOrderByFullNameAsc().stream()
                .map(AssigneeResponse::from)
                .toList();
    }

    @Transactional
    public AdminRequestDetailsResponse updateAdminWorkflow(
            Long requestId, Long adminId, AdminUpdateRequest input
    ) {
        Request request = findAdminRequest(requestId);
        User admin = requireActiveAdmin(adminId);
        RequestStatus oldStatus = request.getStatus();
        if ((oldStatus == RequestStatus.COMPLETED || oldStatus == RequestStatus.REJECTED)
                && input.status() != oldStatus) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Completed or rejected requests cannot be reopened.");
        }
        User assignee = null;
        if (input.assignedToId() != null) {
            assignee = userRepository.findById(input.assignedToId())
                    .filter(User::isActive)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Assignee must be an active user."));
        }
        request.updateWorkflow(input.status(), assignee);
        requestRepository.save(request);
        if (oldStatus != input.status()) {
            String note = input.changeNote() == null || input.changeNote().isBlank()
                    ? "Status changed from " + oldStatus + " to " + input.status() + "."
                    : input.changeNote().trim();
            statusHistoryRepository.save(StatusHistory.create(request, oldStatus, input.status(), admin, note));
        }
        return adminDetails(requestId);
    }

    private Request findAdminRequest(Long requestId) {
        return requestRepository.findAdminDetailsById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found."));
    }

    private User requireActiveAdmin(Long userId) {
        return userRepository.findById(userId)
                .filter(User::isActive)
                .filter(user -> user.getRole() == User.Role.ADMIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access is required."));
    }

    private Specification<Request> adminFilters(
            String search,
            RequestStatus status,
            Long typeId,
            RequestPriority priority
    ) {
        String normalizedSearch = search == null || search.isBlank()
                ? null
                : search.trim().toLowerCase(Locale.ROOT);
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (normalizedSearch != null) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("title")),
                        "%" + normalizedSearch + "%"
                ));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (typeId != null) {
                predicates.add(criteriaBuilder.equal(root.get("type").get("id"), typeId));
            }
            if (priority != null) {
                predicates.add(criteriaBuilder.equal(root.get("priority"), priority));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    @Transactional
    public CreateRequestResponse create(CreateRequestRequest input, Long creatorId) {
        String title = input.title().trim();
        String description = input.description().trim();
        if (title.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title is required.");
        }
        if (title.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title must be at most 200 characters.");
        }
        if (description.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Description is required.");
        }

        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Authentication is required."));
        if (!creator.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account is inactive.");
        }

        RequestType type = requestTypeRepository.findById(input.typeId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Request type does not exist."));
        if (!type.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request type is inactive.");
        }

        Request request = Request.create(title, description, type, input.priority(), creator);
        return CreateRequestResponse.from(requestRepository.save(request));
    }
}
