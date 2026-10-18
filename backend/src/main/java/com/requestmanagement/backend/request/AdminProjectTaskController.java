package com.requestmanagement.backend.request;

import com.requestmanagement.backend.comment.AddCommentRequest;
import com.requestmanagement.backend.comment.CommentResponse;
import com.requestmanagement.backend.comment.CommentService;
import com.requestmanagement.backend.guestcommunication.GuestCommunicationResponse;
import com.requestmanagement.backend.guestcommunication.GuestCommunicationService;
import com.requestmanagement.backend.guestcommunication.SendGuestCommunicationRequest;
import com.requestmanagement.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.requestmanagement.backend.projectstatus.ProjectStatusDtos;

@RestController
@RequestMapping("/api/projects/{projectId}/tasks")
@RequiredArgsConstructor
public class AdminProjectTaskController {
    private final ProjectTaskService projectTaskService;
    private final CommentService commentService;
    private final GuestCommunicationService guestCommunicationService;

    @GetMapping("/board")
    public List<ProjectTaskBoardResponse> board(@PathVariable Long projectId,
                                                @AuthenticationPrincipal AuthenticatedUser principal) {
        return projectTaskService.adminBoard(projectId, principal.userId());
    }

    @GetMapping("/{taskId}")
    public AdminProjectTaskDetailsResponse details(@PathVariable Long projectId, @PathVariable Long taskId,
                                                   @AuthenticationPrincipal AuthenticatedUser principal) {
        return projectTaskService.adminDetails(projectId, taskId, principal.userId());
    }

    @GetMapping("/{taskId}/guest-communications")
    public List<GuestCommunicationResponse> guestCommunications(
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return guestCommunicationService.adminProjectHistory(projectId, taskId, principal.userId());
    }

    @PostMapping("/{taskId}/guest-communications")
    public ResponseEntity<GuestCommunicationResponse> sendGuestCommunication(
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @Valid @RequestBody SendGuestCommunicationRequest input,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                guestCommunicationService.sendAdminProject(
                        projectId, taskId, principal.userId(), input));
    }

    @PostMapping
    public ResponseEntity<ProjectTaskBoardResponse> create(@PathVariable Long projectId,
                                                           @Valid @RequestBody CreateProjectTaskRequest input,
                                                           @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectTaskService.adminCreate(projectId, principal.userId(), input));
    }

    @PostMapping("/{taskId}/comments")
    public ResponseEntity<CommentResponse> comment(@PathVariable Long projectId, @PathVariable Long taskId,
                                                   @Valid @RequestBody AddCommentRequest input,
                                                   @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.addAdminProject(projectId, taskId, principal.userId(), input, false));
    }

    @PostMapping("/{taskId}/internal-notes")
    public ResponseEntity<CommentResponse> internalNote(@PathVariable Long projectId, @PathVariable Long taskId,
                                                        @Valid @RequestBody AddCommentRequest input,
                                                        @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.addAdminProject(projectId, taskId, principal.userId(), input, true));
    }

    @PatchMapping("/{taskId}/status")
    public AdminProjectTaskDetailsResponse status(@PathVariable Long projectId, @PathVariable Long taskId,
                                                  @Valid @RequestBody ProjectStatusDtos.StatusInput input,
                                                  @AuthenticationPrincipal AuthenticatedUser principal) {
        return projectTaskService.adminUpdateStatus(projectId, taskId, principal.userId(), input);
    }

    @PatchMapping("/{taskId}/assignee")
    public AdminProjectTaskDetailsResponse assignee(@PathVariable Long projectId, @PathVariable Long taskId,
                                                    @Valid @RequestBody UpdateProjectTaskAssigneeRequest input,
                                                    @AuthenticationPrincipal AuthenticatedUser principal) {
        return projectTaskService.adminReassign(projectId, taskId, principal.userId(), input);
    }
}
