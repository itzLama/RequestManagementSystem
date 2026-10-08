package com.requestmanagement.backend.request;

import com.requestmanagement.backend.requesttype.RequestType;
import com.requestmanagement.backend.comment.CommentRepository;
import com.requestmanagement.backend.comment.CommentResponse;
import com.requestmanagement.backend.statushistory.StatusHistoryRepository;
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
    private final RequestStatusService requestStatusService;

    @Transactional(readOnly = true)
    public RequestDetailsResponse details(Long requestId, Long creatorId) {
        requireActiveEmployee(creatorId);
        Request request = requestRepository.findByIdAndCreatedBy_IdAndProjectIsNull(requestId, creatorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found."));
        return employeeDetails(request);
    }

    @Transactional(readOnly = true)
    public MyRequestsPageResponse listMine(Long creatorId, int page, int size) {
        requireActiveEmployee(creatorId);
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<MyRequestResponse> results = requestRepository.findByCreatedBy_IdAndProjectIsNull(creatorId, pageable)
                .map(MyRequestResponse::from);
        return MyRequestsPageResponse.from(results);
    }

    @Transactional(readOnly = true)
    public List<MyRequestResponse> boardMine(Long creatorId) {
        requireActiveEmployee(creatorId);
        return requestRepository.findByCreatedBy_IdAndProjectIsNullOrderByCreatedAtDescIdDesc(creatorId).stream()
                .map(MyRequestResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AssignedTasksPageResponse listAssigned(Long assigneeId, int page, int size) {
        requireActiveEmployee(assigneeId);
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<AssignedTaskResponse> results = requestRepository
                .findByAssignedTo_IdAndProjectIsNull(assigneeId, pageable)
                .map(AssignedTaskResponse::from);
        return AssignedTasksPageResponse.from(results);
    }

    @Transactional(readOnly = true)
    public List<AssignedTaskResponse> boardAssigned(Long assigneeId) {
        requireActiveEmployee(assigneeId);
        return requestRepository
                .findByAssignedTo_IdAndProjectIsNullOrderByCreatedAtDescIdDesc(assigneeId)
                .stream().map(AssignedTaskResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RequestDetailsResponse assignedDetails(Long requestId, Long assigneeId) {
        requireActiveEmployee(assigneeId);
        return employeeDetails(findAssignedGeneralRequest(requestId, assigneeId));
    }

    @Transactional
    public RequestDetailsResponse updateAssignedStatus(
            Long requestId, Long assigneeId, UpdateRequestStatusRequest input
    ) {
        User employee = requireActiveEmployee(assigneeId);
        Request request = findAssignedGeneralRequest(requestId, assigneeId);
        requestStatusService.changeStatus(request, input.status(), employee, input.changeNote());
        return employeeDetails(request);
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
        Long currentAssigneeId = request.getAssignedTo() == null ? null : request.getAssignedTo().getId();
        Long requestedAssigneeId = input.assignedToId();
        User assignee = null;
        if (requestedAssigneeId != null) {
            assignee = userRepository.findById(requestedAssigneeId)
                    .filter(User::isActive)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Assignee must be an active user."));
        }
        Long newAssigneeId = assignee == null ? null : assignee.getId();
        requestStatusService.changeStatus(request, input.status(), admin, input.changeNote());
        if (!java.util.Objects.equals(currentAssigneeId, newAssigneeId)) request.assignTo(assignee);
        requestRepository.save(request);
        return adminDetails(requestId);
    }

    private RequestDetailsResponse employeeDetails(Request request) {
        var timeline = statusHistoryRepository.findByRequest_IdOrderByChangedAtAscIdAsc(request.getId()).stream()
                .map(RequestDetailsResponse.TimelineEntry::from).toList();
        var comments = commentRepository.findByRequest_IdAndInternalFalseOrderByCreatedAtAscIdAsc(request.getId()).stream()
                .map(CommentResponse::from).toList();
        return RequestDetailsResponse.from(request, timeline, comments);
    }

    private Request findAssignedGeneralRequest(Long requestId, Long assigneeId) {
        return requestRepository.findByIdAndAssignedTo_IdAndProjectIsNull(requestId, assigneeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found."));
    }

    private Request findAdminRequest(Long requestId) {
        return requestRepository.findAdminDetailsByIdAndProjectIsNull(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found."));
    }

    private User requireActiveAdmin(Long userId) {
        return userRepository.findById(userId)
                .filter(User::isActive)
                .filter(user -> user.getRole() == User.Role.ADMIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access is required."));
    }

    private User requireActiveEmployee(Long userId) {
        return userRepository.findByIdAndRole(userId, User.Role.EMPLOYEE)
                .filter(User::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Active Employee access is required."));
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
            predicates.add(criteriaBuilder.isNull(root.get("project")));
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
