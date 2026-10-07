package com.requestmanagement.backend.request;

import com.requestmanagement.backend.comment.AddCommentRequest;
import com.requestmanagement.backend.comment.CommentResponse;
import com.requestmanagement.backend.comment.CommentService;
import com.requestmanagement.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/mine/{projectId}/tasks")
@RequiredArgsConstructor
public class ProjectTaskController {
    private final ProjectTaskService projectTaskService;
    private final CommentService commentService;

    @GetMapping("/board")
    public List<ProjectTaskBoardResponse> board(@PathVariable Long projectId,
                                                @AuthenticationPrincipal AuthenticatedUser principal) {
        return projectTaskService.board(projectId, principal.userId());
    }

    @GetMapping("/{taskId}")
    public ProjectTaskDetailsResponse details(@PathVariable Long projectId, @PathVariable Long taskId,
                                              @AuthenticationPrincipal AuthenticatedUser principal) {
        return projectTaskService.details(projectId, taskId, principal.userId());
    }

    @PostMapping
    public ResponseEntity<ProjectTaskBoardResponse> create(@PathVariable Long projectId,
                                                           @Valid @RequestBody CreateProjectTaskRequest input,
                                                           @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectTaskService.create(projectId, principal.userId(), input));
    }

    @PostMapping("/{taskId}/comments")
    public ResponseEntity<CommentResponse> comment(@PathVariable Long projectId, @PathVariable Long taskId,
                                                   @Valid @RequestBody AddCommentRequest input,
                                                   @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.addProject(projectId, taskId, principal.userId(), input));
    }

    @PatchMapping("/{taskId}/status")
    public ProjectTaskDetailsResponse status(@PathVariable Long projectId, @PathVariable Long taskId,
                                             @Valid @RequestBody UpdateRequestStatusRequest input,
                                             @AuthenticationPrincipal AuthenticatedUser principal) {
        return projectTaskService.updateStatus(projectId, taskId, principal.userId(), input);
    }

    @PatchMapping("/{taskId}/assignee")
    public ProjectTaskDetailsResponse assignee(@PathVariable Long projectId, @PathVariable Long taskId,
                                               @Valid @RequestBody UpdateProjectTaskAssigneeRequest input,
                                               @AuthenticationPrincipal AuthenticatedUser principal) {
        return projectTaskService.reassign(projectId, taskId, principal.userId(), input);
    }
}
