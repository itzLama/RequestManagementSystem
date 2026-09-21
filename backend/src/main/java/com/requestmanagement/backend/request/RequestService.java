package com.requestmanagement.backend.request;

import com.requestmanagement.backend.requesttype.RequestType;
import com.requestmanagement.backend.comment.CommentRepository;
import com.requestmanagement.backend.comment.CommentResponse;
import com.requestmanagement.backend.statushistory.StatusHistoryRepository;
import com.requestmanagement.backend.requesttype.RequestTypeRepository;
import com.requestmanagement.backend.user.User;
import com.requestmanagement.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
