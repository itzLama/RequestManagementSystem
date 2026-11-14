package com.requestmanagement.backend.request;

import com.requestmanagement.backend.comment.CommentRepository;
import com.requestmanagement.backend.comment.CommentResponse;
import com.requestmanagement.backend.project.Project;
import com.requestmanagement.backend.project.ProjectAccessService;
import com.requestmanagement.backend.statushistory.StatusHistoryRepository;
import com.requestmanagement.backend.user.User;
import com.requestmanagement.backend.user.UserRepository;
import com.requestmanagement.backend.project.ProjectRepository;
import com.requestmanagement.backend.projectworktype.ProjectWorkType;
import com.requestmanagement.backend.projectworktype.ProjectWorkTypeService;
import com.requestmanagement.backend.guestform.RequestGuestFieldAnswerRepository;
import com.requestmanagement.backend.guestform.GuestAnswerDisplayService;
import com.requestmanagement.backend.guestform.GuestFormDtos.AnswerResponse;
import com.requestmanagement.backend.projectstatus.*;
import com.requestmanagement.backend.attachment.AttachmentDeletionService;
import com.requestmanagement.backend.usernotification.TaskNotificationPublisher;
import com.requestmanagement.backend.usernotification.ProjectChangePublisher;
import com.requestmanagement.backend.usernotification.ProjectChangeEvent.ChangeType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectTaskService {
    private final ProjectAccessService projectAccessService;
    private final RequestRepository requestRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectWorkTypeService projectWorkTypeService;
    private final RequestGuestFieldAnswerRepository guestFieldAnswerRepository;
    private final ProjectStatusService projectStatusService;
    private final GuestAnswerDisplayService guestAnswerDisplayService;
    private final RequestPermanentDeletionService permanentDeletionService;
    private final RichDescriptionValidator richDescriptionValidator;
    private final AttachmentDeletionService attachmentDeletionService;
    private final TaskNotificationPublisher notifications;
    private final ProjectChangePublisher liveUpdates;

    @Transactional(readOnly = true)
    public List<ProjectTaskBoardResponse> board(Long projectId, Long employeeId) {
        projectAccessService.requireAccessibleProject(projectId, employeeId);
        return requestRepository.findByProject_IdAndParentTaskIsNullOrderByCreatedAtDescIdDesc(projectId)
                .stream().map(ProjectTaskBoardResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ProjectTaskDetailsResponse details(Long projectId, Long taskId, Long employeeId) {
        projectAccessService.requireAccessibleProject(projectId, employeeId);
        return details(findTask(projectId, taskId));
    }

    @Transactional
    public ProjectTaskBoardResponse create(Long projectId, Long employeeId, CreateProjectTaskRequest input) {
        Project project = projectAccessService.requireMutableProject(projectId, employeeId);
        User creator = projectAccessService.requireActiveEmployee(employeeId);
        User assignee = projectAccessService.requireEligibleAssignee(project, input.assignedToId());
        ProjectWorkType workType = projectWorkTypeService.requireActiveForProject(projectId, input.workTypeId());
        String title = input.title().trim();
        CreationDescription description = creationDescription(input.description(), input.descriptionDocument());
        if (title.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title is required.");
        Request task = Request.createProjectTask(title, description.plainText(), workType, input.priority(),
                creator, assignee, project, projectStatusService.initial(projectId));
        if (description.document() != null) task.updateRichDescription(description.document(), description.plainText());
        schedule(task, input.startDate(), input.dueDate());
        Request saved = requestRepository.save(task);
        notifications.assigned(saved, creator);
        liveUpdates.changed(saved, ChangeType.TASK_CREATED);
        return ProjectTaskBoardResponse.from(saved);
    }

    @Transactional
    public ProjectTaskDetailsResponse updateDates(Long projectId, Long taskId, Long employeeId, TaskDatesInput input) {
        projectAccessService.requireMutableProject(projectId, employeeId);
        projectAccessService.requireActiveEmployee(employeeId);
        Request task = findTask(projectId, taskId);
        schedule(task, input.startDate(), input.dueDate());
        requestRepository.save(task);
        liveUpdates.changed(task, ChangeType.DATES_CHANGED);
        return details(task);
    }

    @Transactional
    public ProjectTaskDetailsResponse createSubtask(Long projectId, Long parentId, Long employeeId,
                                                    SubtaskDtos.CreateInput input) {
        Project project = projectAccessService.requireMutableProject(projectId, employeeId);
        User creator = projectAccessService.requireActiveEmployee(employeeId);
        Request parent = requireRootParent(projectId, parentId);
        Request subtask = requestRepository.save(createSubtask(project, parent, creator, input));
        notifications.assigned(subtask, creator);
        liveUpdates.changed(subtask, ChangeType.TASK_CREATED);
        return details(subtask);
    }

    @Transactional
    public ProjectTaskDetailsResponse updateSubtask(Long projectId, Long parentId, Long subtaskId,
                                                    Long employeeId, SubtaskDtos.UpdateInput input) {
        projectAccessService.requireMutableProject(projectId, employeeId);
        projectAccessService.requireActiveEmployee(employeeId);
        Request subtask = requireSubtask(projectId, parentId, subtaskId);
        updateFields(projectId, subtask, input);
        liveUpdates.changed(subtask, ChangeType.TASK_UPDATED);
        return details(subtask);
    }

    @Transactional
    public ProjectTaskDetailsResponse updateStatus(Long projectId, Long taskId, Long employeeId,
                                                   ProjectStatusDtos.StatusInput input) {
        projectAccessService.requireMutableProject(projectId, employeeId);
        User actor = projectAccessService.requireActiveEmployee(employeeId);
        Request task = findTask(projectId, taskId);
        projectStatusService.change(task, projectStatusService.resolveStatusId(projectId, input), actor, input.changeNote());
        return details(task);
    }

    @Transactional
    public ProjectTaskDetailsResponse reassign(Long projectId, Long taskId, Long employeeId,
                                               UpdateProjectTaskAssigneeRequest input) {
        Project project = projectAccessService.requireMutableProject(projectId, employeeId);
        User actor = projectAccessService.requireActiveEmployee(employeeId);
        Request task = findTask(projectId, taskId);
        User assignee = projectAccessService.requireEligibleAssignee(project, input.assignedToId());
        if (task.getAssignedTo() == null || !task.getAssignedTo().getId().equals(assignee.getId())) {
            task.assignTo(assignee);
            requestRepository.save(task);
            notifications.assigned(task, actor);
            liveUpdates.changed(task, ChangeType.ASSIGNEE_CHANGED);
        }
        return details(task);
    }

    @Transactional
    public RichDescriptionDtos.Response updateRichDescription(Long projectId, Long taskId, Long employeeId,
                                                               RichDescriptionDtos.UpdateInput input) {
        projectAccessService.requireMutableProject(projectId, employeeId);
        projectAccessService.requireActiveEmployee(employeeId);
        Request task = findTask(projectId, taskId);
        return updateRichDescription(task, input);
    }

    @Transactional(readOnly = true)
    public List<ProjectTaskBoardResponse> adminBoard(Long projectId, Long adminId) {
        requireActiveAdmin(adminId);
        requireProject(projectId);
        return requestRepository.findByProject_IdAndParentTaskIsNullOrderByCreatedAtDescIdDesc(projectId)
                .stream().map(ProjectTaskBoardResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AdminProjectTaskDetailsResponse adminDetails(Long projectId, Long taskId, Long adminId) {
        requireActiveAdmin(adminId);
        return adminDetails(findTask(projectId, taskId));
    }

    @Transactional
    public ProjectTaskBoardResponse adminCreate(Long projectId, Long adminId, CreateProjectTaskRequest input) {
        User admin = requireActiveAdmin(adminId);
        Project project = requireProject(projectId);
        projectAccessService.requireActive(project);
        User assignee = projectAccessService.requireEligibleAssignee(project, input.assignedToId());
        ProjectWorkType workType = projectWorkTypeService.requireActiveForProject(projectId, input.workTypeId());
        String title = input.title().trim();
        CreationDescription description = creationDescription(input.description(), input.descriptionDocument());
        if (title.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title is required.");
        Request task = Request.createProjectTask(title, description.plainText(), workType, input.priority(),
                admin, assignee, project, projectStatusService.initial(projectId));
        if (description.document() != null) task.updateRichDescription(description.document(), description.plainText());
        schedule(task, input.startDate(), input.dueDate());
        Request saved = requestRepository.save(task);
        notifications.assigned(saved, admin);
        liveUpdates.changed(saved, ChangeType.TASK_CREATED);
        return ProjectTaskBoardResponse.from(saved);
    }

    @Transactional
    public AdminProjectTaskDetailsResponse adminUpdateDates(Long projectId, Long taskId, Long adminId,
                                                            TaskDatesInput input) {
        requireActiveAdmin(adminId);
        Project project = requireProject(projectId);
        projectAccessService.requireActive(project);
        Request task = findTask(projectId, taskId);
        schedule(task, input.startDate(), input.dueDate());
        requestRepository.save(task);
        liveUpdates.changed(task, ChangeType.DATES_CHANGED);
        return adminDetails(task);
    }

    @Transactional
    public AdminProjectTaskDetailsResponse adminCreateSubtask(Long projectId, Long parentId, Long adminId,
                                                              SubtaskDtos.CreateInput input) {
        User admin = requireActiveAdmin(adminId);
        Project project = requireProject(projectId);
        projectAccessService.requireActive(project);
        Request parent = requireRootParent(projectId, parentId);
        Request subtask = requestRepository.save(createSubtask(project, parent, admin, input));
        notifications.assigned(subtask, admin);
        liveUpdates.changed(subtask, ChangeType.TASK_CREATED);
        return adminDetails(subtask);
    }

    @Transactional
    public AdminProjectTaskDetailsResponse adminUpdateSubtask(Long projectId, Long parentId, Long subtaskId,
                                                              Long adminId, SubtaskDtos.UpdateInput input) {
        requireActiveAdmin(adminId);
        Project project = requireProject(projectId);
        projectAccessService.requireActive(project);
        Request subtask = requireSubtask(projectId, parentId, subtaskId);
        updateFields(projectId, subtask, input);
        liveUpdates.changed(subtask, ChangeType.TASK_UPDATED);
        return adminDetails(subtask);
    }

    @Transactional
    public AdminProjectTaskDetailsResponse adminUpdateStatus(Long projectId, Long taskId, Long adminId,
                                                             ProjectStatusDtos.StatusInput input) {
        User admin = requireActiveAdmin(adminId);
        Project project = requireProject(projectId);
        projectAccessService.requireActive(project);
        Request task = findTask(projectId, taskId);
        projectStatusService.change(task, projectStatusService.resolveStatusId(projectId, input), admin, input.changeNote());
        return adminDetails(task);
    }

    @Transactional
    public AdminProjectTaskDetailsResponse adminReassign(Long projectId, Long taskId, Long adminId,
                                                         UpdateProjectTaskAssigneeRequest input) {
        User admin = requireActiveAdmin(adminId);
        Project project = requireProject(projectId);
        projectAccessService.requireActive(project);
        Request task = findTask(projectId, taskId);
        User assignee = projectAccessService.requireEligibleAssignee(project, input.assignedToId());
        if (task.getAssignedTo() == null || !task.getAssignedTo().getId().equals(assignee.getId())) {
            task.assignTo(assignee);
            requestRepository.save(task);
            notifications.assigned(task, admin);
            liveUpdates.changed(task, ChangeType.ASSIGNEE_CHANGED);
        }
        return adminDetails(task);
    }

    @Transactional
    public RichDescriptionDtos.Response adminUpdateRichDescription(Long projectId, Long taskId, Long adminId,
                                                                    RichDescriptionDtos.UpdateInput input) {
        requireActiveAdmin(adminId);
        Project project = requireProject(projectId);
        projectAccessService.requireActive(project);
        Request task = findTask(projectId, taskId);
        return updateRichDescription(task, input);
    }

    public void permanentlyDeleteAdminTask(Long projectId, Long taskId, Long adminId, boolean deleteSubtasks) {
        requireActiveAdmin(adminId);
        Project project = requireProject(projectId);
        projectAccessService.requireActive(project);
        Request task = requestRepository.findByIdAndProject_Id(taskId, projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found."));
        List<Request> subtasks = requestRepository.findByParentTask_IdOrderByCreatedAtAscIdAsc(taskId);
        if (!subtasks.isEmpty() && !deleteSubtasks) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This parent Task has " + subtasks.size() + " subtasks. Explicitly confirm Delete All.");
        }
        if (deleteSubtasks) {
            if (task.isSubtask()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Delete All is only valid for a parent Task.");
            List<Long> ids = new java.util.ArrayList<>(subtasks.stream().map(Request::getId).toList());
            ids.add(task.getId());
            attachmentDeletionService.beginPermanentTaskDeletion(ids);
            attachmentDeletionService.cleanupForPermanentDeletion(ids);
            permanentDeletionService.deleteParentWithSubtasks(task, subtasks);
            subtasks.forEach(subtask -> liveUpdates.changed(projectId, subtask.getId(), ChangeType.TASK_DELETED));
        } else {
            attachmentDeletionService.beginPermanentTaskDeletion(List.of(task.getId()));
            attachmentDeletionService.cleanupForPermanentDeletion(List.of(task.getId()));
            permanentDeletionService.delete(task);
        }
        liveUpdates.changed(projectId, taskId, ChangeType.TASK_DELETED);
    }

    @Transactional(readOnly = true)
    public List<SubtaskDtos.Summary> subtasks(Long projectId, Long parentId, Long employeeId) {
        projectAccessService.requireAccessibleProject(projectId, employeeId);
        requireRootParent(projectId, parentId);
        return subtaskSummaries(parentId);
    }

    @Transactional(readOnly = true)
    public List<SubtaskDtos.Summary> adminSubtasks(Long projectId, Long parentId, Long adminId) {
        requireActiveAdmin(adminId);
        requireProject(projectId);
        requireRootParent(projectId, parentId);
        return subtaskSummaries(parentId);
    }

    private Request findTask(Long projectId, Long taskId) {
        return requestRepository.findByIdAndProject_Id(taskId, projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found."));
    }

    private ProjectTaskDetailsResponse details(Request task) {
        var timeline = statusHistoryRepository.findByRequest_IdOrderByChangedAtAscIdAsc(task.getId()).stream()
                .map(RequestDetailsResponse.TimelineEntry::from).toList();
        var comments = commentRepository.findByRequest_IdAndInternalFalseOrderByCreatedAtAscIdAsc(task.getId())
                .stream().map(CommentResponse::from).toList();
        var answers = guestFieldAnswerRepository.findByRequest_IdOrderByDisplayOrderAscIdAsc(task.getId()).stream()
                .map(guestAnswerDisplayService::currentLabels).toList();
        List<Request> subtasks = task.isSubtask() ? List.of() : childTasks(task.getId());
        return ProjectTaskDetailsResponse.from(task, timeline, comments, answers,
                subtasks.stream().map(SubtaskDtos.Summary::from).toList(), SubtaskDtos.Progress.from(subtasks));
    }

    private AdminProjectTaskDetailsResponse adminDetails(Request task) {
        var timeline = statusHistoryRepository.findByRequest_IdOrderByChangedAtAscIdAsc(task.getId()).stream()
                .map(RequestDetailsResponse.TimelineEntry::from).toList();
        var comments = commentRepository.findByRequest_IdAndInternalFalseOrderByCreatedAtAscIdAsc(task.getId())
                .stream().map(CommentResponse::from).toList();
        var internalNotes = commentRepository.findByRequest_IdAndInternalTrueOrderByCreatedAtAscIdAsc(task.getId())
                .stream().map(CommentResponse::from).toList();
        var answers = guestFieldAnswerRepository.findByRequest_IdOrderByDisplayOrderAscIdAsc(task.getId()).stream()
                .map(guestAnswerDisplayService::currentLabels).toList();
        List<Request> subtasks = task.isSubtask() ? List.of() : childTasks(task.getId());
        return AdminProjectTaskDetailsResponse.from(task, timeline, comments, internalNotes, answers,
                subtasks.stream().map(SubtaskDtos.Summary::from).toList(), SubtaskDtos.Progress.from(subtasks));
    }

    private Request createSubtask(Project project, Request parent, User creator, SubtaskDtos.CreateInput input) {
        User assignee = projectAccessService.requireEligibleAssignee(project, input.assignedToId());
        ProjectWorkType workType = projectWorkTypeService.requireActiveForProject(project.getId(), input.workTypeId());
        ProjectStatusColumn status = projectStatusService.requireActive(project.getId(), input.statusId());
        String title = normalized(input.title(), "Title is required.");
        CreationDescription description = creationDescription(input.description(), input.descriptionDocument());
        Request subtask = Request.createProjectTask(title, description.plainText(), workType, input.priority(),
                creator, assignee, project, status, parent);
        if (description.document() != null) {
            subtask.updateRichDescription(description.document(), description.plainText());
        }
        return subtask;
    }

    private void updateFields(Long projectId, Request task, SubtaskDtos.UpdateInput input) {
        String title = normalized(input.title(), "Title is required.");
        ProjectWorkType workType = projectWorkTypeService.requireActiveForProject(projectId, input.workTypeId());
        task.updateTaskMetadata(title, workType, input.priority());
        if (input.description() != null) {
            task.replacePlainDescription(normalized(input.description(), "Description is required."));
        }
        requestRepository.save(task);
    }

    private RichDescriptionDtos.Response updateRichDescription(Request task,
                                                                RichDescriptionDtos.UpdateInput input) {
        if (task.isGuest()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Guest-submitted Tasks use plain-text descriptions.");
        }
        RichDescriptionValidator.ValidatedDocument validated = richDescriptionValidator.validate(input.document());
        task.updateRichDescription(validated.document(), validated.plainText());
        requestRepository.saveAndFlush(task);
        liveUpdates.changed(task, ChangeType.TASK_UPDATED);
        return RichDescriptionDtos.Response.from(task);
    }

    private void schedule(Request task, java.time.LocalDate startDate, java.time.LocalDate dueDate) {
        if (startDate != null && dueDate != null && startDate.isAfter(dueDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date must be on or before the due date.");
        }
        task.schedule(startDate, dueDate);
    }

    private String normalized(String value, String message) {
        if (value == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        return normalized;
    }

    private CreationDescription creationDescription(String plainText, tools.jackson.databind.JsonNode document) {
        if (document != null) {
            RichDescriptionValidator.ValidatedDocument validated = richDescriptionValidator.validate(document);
            return new CreationDescription(validated.plainText(), validated.document());
        }
        return new CreationDescription(normalized(plainText, "Description is required."), null);
    }

    private record CreationDescription(String plainText, tools.jackson.databind.JsonNode document) { }

    private Request requireRootParent(Long projectId, Long parentId) {
        Request parent = findTask(projectId, parentId);
        if (parent.isSubtask()) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Nested subtasks are not allowed.");
        return parent;
    }

    private Request requireSubtask(Long projectId, Long parentId, Long subtaskId) {
        Request task = findTask(projectId, subtaskId);
        if (!task.isSubtask() || !task.getParentTask().getId().equals(parentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subtask not found.");
        }
        return task;
    }

    private List<Request> childTasks(Long parentId) {
        return requestRepository.findByParentTask_IdOrderByCreatedAtAscIdAsc(parentId);
    }

    private List<SubtaskDtos.Summary> subtaskSummaries(Long parentId) {
        return childTasks(parentId).stream().map(SubtaskDtos.Summary::from).toList();
    }

    private User requireActiveAdmin(Long adminId) {
        return userRepository.findById(adminId)
                .filter(User::isActive)
                .filter(user -> user.getRole() == User.Role.ADMIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Administrator access is required."));
    }

    private Project requireProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found."));
    }
}
