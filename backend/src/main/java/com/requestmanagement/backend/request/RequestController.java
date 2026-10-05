package com.requestmanagement.backend.request;

import com.requestmanagement.backend.security.AuthenticatedUser;
import com.requestmanagement.backend.comment.AddCommentRequest;
import com.requestmanagement.backend.comment.CommentResponse;
import com.requestmanagement.backend.comment.CommentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
public class RequestController {

    private final RequestService requestService;
    private final CommentService commentService;

    @GetMapping("/admin/assignees")
    public List<AssigneeResponse> assignees() {
        return requestService.assignees();
    }

    @GetMapping("/admin/{id}")
    public AdminRequestDetailsResponse adminDetails(@PathVariable Long id) {
        return requestService.adminDetails(id);
    }

    @org.springframework.web.bind.annotation.PatchMapping("/admin/{id}")
    public AdminRequestDetailsResponse updateAdminWorkflow(
            @PathVariable Long id,
            @Valid @RequestBody AdminUpdateRequest input,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return requestService.updateAdminWorkflow(id, principal.userId(), input);
    }

    @PostMapping("/admin/{id}/comments")
    public ResponseEntity<CommentResponse> addAdminComment(
            @PathVariable Long id,
            @Valid @RequestBody AddCommentRequest input,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.addAdmin(id, principal.userId(), input, false));
    }

    @PostMapping("/admin/{id}/internal-notes")
    public ResponseEntity<CommentResponse> addInternalNote(
            @PathVariable Long id,
            @Valid @RequestBody AddCommentRequest input,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.addAdmin(id, principal.userId(), input, true));
    }

    @GetMapping("/admin/board")
    public List<AdminRequestResponse> boardAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) Long typeId,
            @RequestParam(required = false) RequestPriority priority
    ) {
        return requestService.boardAll(search, status, typeId, priority);
    }

    @GetMapping("/admin")
    public AdminRequestsPageResponse listAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) Long typeId,
            @RequestParam(required = false) RequestPriority priority,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size
    ) {
        return requestService.listAll(search, status, typeId, priority, page, size);
    }

    @GetMapping("/{id}")
    public RequestDetailsResponse details(@PathVariable Long id,
                                          @AuthenticationPrincipal AuthenticatedUser principal) {
        return requestService.details(id, principal.userId());
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<CommentResponse> addComment(@PathVariable Long id,
                                                       @Valid @RequestBody AddCommentRequest input,
                                                       @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commentService.add(id, principal.userId(), input));
    }

    @GetMapping("/mine")
    public MyRequestsPageResponse listMine(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size
    ) {
        return requestService.listMine(principal.userId(), page, size);
    }

    @GetMapping("/mine/board")
    public List<MyRequestResponse> boardMine(@AuthenticationPrincipal AuthenticatedUser principal) {
        return requestService.boardMine(principal.userId());
    }

    @GetMapping("/assigned")
    public MyRequestsPageResponse listAssigned(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size
    ) {
        return requestService.listAssigned(principal.userId(), page, size);
    }

    @GetMapping("/assigned/board")
    public List<MyRequestResponse> boardAssigned(@AuthenticationPrincipal AuthenticatedUser principal) {
        return requestService.boardAssigned(principal.userId());
    }

    @GetMapping("/assigned/{id}")
    public RequestDetailsResponse assignedDetails(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return requestService.assignedDetails(id, principal.userId());
    }

    @PostMapping("/assigned/{id}/comments")
    public ResponseEntity<CommentResponse> addAssignedComment(
            @PathVariable Long id,
            @Valid @RequestBody AddCommentRequest input,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.addAssigned(id, principal.userId(), input));
    }

    @org.springframework.web.bind.annotation.PatchMapping("/assigned/{id}/status")
    public RequestDetailsResponse updateAssignedStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRequestStatusRequest input,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return requestService.updateAssignedStatus(id, principal.userId(), input);
    }

    @PostMapping
    public ResponseEntity<CreateRequestResponse> create(
            @Valid @RequestBody CreateRequestRequest input,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        CreateRequestResponse response = requestService.create(input, principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

}
