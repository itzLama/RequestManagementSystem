package com.requestmanagement.backend.request;

import com.requestmanagement.backend.comment.AddCommentRequest;
import com.requestmanagement.backend.comment.CommentResponse;
import com.requestmanagement.backend.comment.CommentService;
import com.requestmanagement.backend.guestcommunication.GuestCommunicationResponse;
import com.requestmanagement.backend.guestcommunication.GuestCommunicationService;
import com.requestmanagement.backend.guestcommunication.SendGuestCommunicationRequest;
import com.requestmanagement.backend.security.AuthenticatedUser;
import com.requestmanagement.backend.tasklink.ProjectTaskLinkDtos;
import com.requestmanagement.backend.tasklink.ProjectTaskLinkService;
import com.requestmanagement.backend.attachment.AttachmentMetadataResponse;
import com.requestmanagement.backend.attachment.InternalAttachmentService;
import com.requestmanagement.backend.attachment.AttachmentScanService;
import com.requestmanagement.backend.attachment.AttachmentDeletionService;
import com.requestmanagement.backend.attachment.AttachmentVisibilityService;
import com.requestmanagement.backend.attachment.GuestVisibilityRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import com.requestmanagement.backend.projectstatus.ProjectStatusDtos;

@RestController
@RequestMapping("/api/projects/{projectId}/tasks")
@RequiredArgsConstructor
public class AdminProjectTaskController {
    private final ProjectTaskService projectTaskService;
    private final CommentService commentService;
    private final GuestCommunicationService guestCommunicationService;
    private final ProjectTaskLinkService taskLinkService;
    private final InternalAttachmentService attachmentService;
    private final AttachmentScanService attachmentScanService;
    private final AttachmentDeletionService attachmentDeletionService;
    private final AttachmentVisibilityService attachmentVisibilityService;

    @PostMapping(path = "/{taskId}/attachments", consumes = "multipart/form-data")
    public ResponseEntity<AttachmentMetadataResponse> uploadAttachment(
            @PathVariable Long projectId, @PathVariable Long taskId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.accepted().body(
                attachmentService.adminUpload(projectId, taskId, principal.userId(), file));
    }

    @GetMapping("/{taskId}/attachments")
    public List<AttachmentMetadataResponse> attachments(
            @PathVariable Long projectId, @PathVariable Long taskId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return attachmentService.adminList(projectId, taskId, principal.userId());
    }

    @PostMapping("/{taskId}/attachments/{attachmentId}/scan")
    public AttachmentMetadataResponse scanAttachment(
            @PathVariable Long projectId, @PathVariable Long taskId, @PathVariable Long attachmentId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return attachmentScanService.adminProcess(projectId, taskId, attachmentId, principal.userId());
    }

    @GetMapping("/{taskId}/attachments/{attachmentId}/content")
    public ResponseEntity<StreamingResponseBody> downloadAttachment(
            @PathVariable Long projectId, @PathVariable Long taskId, @PathVariable Long attachmentId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return attachmentService.adminDownload(projectId, taskId, attachmentId, principal.userId());
    }

    @DeleteMapping("/{taskId}/attachments/{attachmentId}")
    public ResponseEntity<Void> deleteAttachment(
            @PathVariable Long projectId, @PathVariable Long taskId, @PathVariable Long attachmentId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        attachmentDeletionService.adminDelete(projectId, taskId, attachmentId, principal.userId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{taskId}/attachments/{attachmentId}/guest-visibility")
    public AttachmentMetadataResponse guestVisibility(
            @PathVariable Long projectId, @PathVariable Long taskId, @PathVariable Long attachmentId,
            @Valid @RequestBody GuestVisibilityRequest input,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return attachmentVisibilityService.adminChange(projectId, taskId, attachmentId,
                principal.userId(), input.guestVisible());
    }

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

    @GetMapping("/{taskId}/links")
    public List<ProjectTaskLinkDtos.Response> links(@PathVariable Long projectId, @PathVariable Long taskId,
                                                    @AuthenticationPrincipal AuthenticatedUser principal) {
        return taskLinkService.adminList(projectId, taskId, principal.userId());
    }

    @GetMapping("/{taskId}/link-candidates")
    public List<ProjectTaskLinkDtos.Candidate> linkCandidates(
            @PathVariable Long projectId, @PathVariable Long taskId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return taskLinkService.adminCandidates(projectId, taskId, principal.userId());
    }

    @PostMapping("/{taskId}/links")
    public ResponseEntity<ProjectTaskLinkDtos.Response> createLink(
            @PathVariable Long projectId, @PathVariable Long taskId,
            @Valid @RequestBody ProjectTaskLinkDtos.CreateInput input,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(taskLinkService.adminCreate(projectId, taskId, principal.userId(), input));
    }

    @DeleteMapping("/{taskId}/links/{linkId}")
    public ResponseEntity<Void> deleteLink(@PathVariable Long projectId, @PathVariable Long taskId,
                                           @PathVariable Long linkId,
                                           @AuthenticationPrincipal AuthenticatedUser principal) {
        taskLinkService.adminDelete(projectId, taskId, linkId, principal.userId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> permanentlyDelete(
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "false") boolean deleteSubtasks,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        projectTaskService.permanentlyDeleteAdminTask(projectId, taskId, principal.userId(), deleteSubtasks);
        return ResponseEntity.noContent().build();
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

    @GetMapping("/{taskId}/subtasks")
    public List<SubtaskDtos.Summary> subtasks(@PathVariable Long projectId, @PathVariable Long taskId,
                                              @AuthenticationPrincipal AuthenticatedUser principal) {
        return projectTaskService.adminSubtasks(projectId, taskId, principal.userId());
    }

    @PostMapping("/{taskId}/subtasks")
    public ResponseEntity<AdminProjectTaskDetailsResponse> createSubtask(
            @PathVariable Long projectId, @PathVariable Long taskId,
            @Valid @RequestBody SubtaskDtos.CreateInput input,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectTaskService.adminCreateSubtask(projectId, taskId, principal.userId(), input));
    }

    @PutMapping("/{taskId}/subtasks/{subtaskId}")
    public AdminProjectTaskDetailsResponse updateSubtask(
            @PathVariable Long projectId, @PathVariable Long taskId, @PathVariable Long subtaskId,
            @Valid @RequestBody SubtaskDtos.UpdateInput input,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return projectTaskService.adminUpdateSubtask(projectId, taskId, subtaskId, principal.userId(), input);
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

    @PutMapping("/{taskId}/description-document")
    public RichDescriptionDtos.Response updateRichDescription(
            @PathVariable Long projectId, @PathVariable Long taskId,
            @Valid @RequestBody RichDescriptionDtos.UpdateInput input,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return projectTaskService.adminUpdateRichDescription(projectId, taskId, principal.userId(), input);
    }
}
