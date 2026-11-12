package com.requestmanagement.backend.attachment;

import com.requestmanagement.backend.project.Project;
import com.requestmanagement.backend.project.ProjectAccessService;
import com.requestmanagement.backend.project.ProjectRepository;
import com.requestmanagement.backend.request.RequestRepository;
import com.requestmanagement.backend.user.User;
import com.requestmanagement.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AttachmentVisibilityService {
    private final AttachmentLifecycleService lifecycle;
    private final ProjectAccessService access;
    private final ProjectRepository projects;
    private final RequestRepository requests;
    private final UserRepository users;

    public AttachmentMetadataResponse adminChange(Long projectId, Long taskId, Long attachmentId,
                                                   Long adminId, boolean guestVisible) {
        requireAdmin(adminId);
        Project project = requireProject(projectId);
        access.requireActive(project);
        requireTask(projectId, taskId);
        return change(projectId, taskId, attachmentId, adminId, guestVisible);
    }

    public AttachmentMetadataResponse employeeChange(Long projectId, Long taskId, Long attachmentId,
                                                      Long employeeId, boolean guestVisible) {
        access.requireActiveEmployee(employeeId);
        access.requireMutableProject(projectId, employeeId);
        requireTask(projectId, taskId);
        return change(projectId, taskId, attachmentId, employeeId, guestVisible);
    }

    private AttachmentMetadataResponse change(Long projectId, Long taskId, Long attachmentId,
                                              Long actorId, boolean guestVisible) {
        TaskAttachment current = lifecycle.scopedMetadata(projectId, taskId, attachmentId);
        TaskAttachment.Visibility target = guestVisible
                ? TaskAttachment.Visibility.GUEST_VISIBLE : TaskAttachment.Visibility.INTERNAL_ONLY;
        if (current.getUploadSource() == TaskAttachment.UploadSource.GUEST
                && target == TaskAttachment.Visibility.INTERNAL_ONLY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Guest-uploaded attachments cannot be hidden from their verified Guest.");
        }
        try {
            return AttachmentMetadataResponse.from(
                    lifecycle.changeGuestVisibility(projectId, taskId, attachmentId, actorId, target));
        } catch (AttachmentLifecycleException exception) {
            HttpStatus status = exception.getReason() == AttachmentLifecycleException.Reason.INVALID_OWNER
                    || exception.getReason() == AttachmentLifecycleException.Reason.NOT_FOUND
                    ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT;
            throw new ResponseStatusException(status, exception.getMessage());
        }
    }

    private Project requireProject(Long id) {
        return projects.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found."));
    }

    private void requireTask(Long projectId, Long taskId) {
        if (!requests.existsByIdAndProject_Id(taskId, projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found.");
        }
    }

    private void requireAdmin(Long id) {
        users.findById(id).filter(User::isActive).filter(u -> u.getRole() == User.Role.ADMIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Administrator access is required."));
    }
}
