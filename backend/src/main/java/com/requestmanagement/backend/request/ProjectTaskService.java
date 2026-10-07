package com.requestmanagement.backend.request;

import com.requestmanagement.backend.comment.CommentRepository;
import com.requestmanagement.backend.comment.CommentResponse;
import com.requestmanagement.backend.project.Project;
import com.requestmanagement.backend.project.ProjectAccessService;
import com.requestmanagement.backend.statushistory.StatusHistoryRepository;
import com.requestmanagement.backend.user.User;
import com.requestmanagement.backend.user.UserRepository;
import com.requestmanagement.backend.project.ProjectRepository;
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
    private final RequestStatusService requestStatusService;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;

    @Transactional(readOnly = true)
    public List<ProjectTaskBoardResponse> board(Long projectId, Long employeeId) {
        projectAccessService.requireAccessibleProject(projectId, employeeId);
        return requestRepository.findByProject_IdOrderByCreatedAtDescIdDesc(projectId)
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
        String title = input.title().trim();
        String description = input.description().trim();
        if (title.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title is required.");
        if (description.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Description is required.");
        Request task = Request.createProjectTask(title, description, input.workType(), input.priority(),
                creator, assignee, project);
        return ProjectTaskBoardResponse.from(requestRepository.save(task));
    }

    @Transactional
    public ProjectTaskDetailsResponse updateStatus(Long projectId, Long taskId, Long employeeId,
                                                   UpdateRequestStatusRequest input) {
        projectAccessService.requireMutableProject(projectId, employeeId);
        User actor = projectAccessService.requireActiveEmployee(employeeId);
        Request task = findTask(projectId, taskId);
        requestStatusService.changeStatus(task, input.status(), actor, input.changeNote());
        return details(task);
    }

    @Transactional
    public ProjectTaskDetailsResponse reassign(Long projectId, Long taskId, Long employeeId,
                                               UpdateProjectTaskAssigneeRequest input) {
        Project project = projectAccessService.requireMutableProject(projectId, employeeId);
        Request task = findTask(projectId, taskId);
        User assignee = projectAccessService.requireEligibleAssignee(project, input.assignedToId());
        if (task.getAssignedTo() == null || !task.getAssignedTo().getId().equals(assignee.getId())) {
            task.assignTo(assignee);
            requestRepository.save(task);
        }
        return details(task);
    }

    @Transactional(readOnly = true)
    public List<ProjectTaskBoardResponse> adminBoard(Long projectId, Long adminId) {
        requireActiveAdmin(adminId);
        requireProject(projectId);
        return requestRepository.findByProject_IdOrderByCreatedAtDescIdDesc(projectId)
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
        String title = input.title().trim();
        String description = input.description().trim();
        if (title.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title is required.");
        if (description.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Description is required.");
        return ProjectTaskBoardResponse.from(requestRepository.save(Request.createProjectTask(
                title, description, input.workType(), input.priority(), admin, assignee, project)));
    }

    @Transactional
    public AdminProjectTaskDetailsResponse adminUpdateStatus(Long projectId, Long taskId, Long adminId,
                                                             UpdateRequestStatusRequest input) {
        User admin = requireActiveAdmin(adminId);
        Project project = requireProject(projectId);
        projectAccessService.requireActive(project);
        Request task = findTask(projectId, taskId);
        requestStatusService.changeStatus(task, input.status(), admin, input.changeNote());
        return adminDetails(task);
    }

    @Transactional
    public AdminProjectTaskDetailsResponse adminReassign(Long projectId, Long taskId, Long adminId,
                                                         UpdateProjectTaskAssigneeRequest input) {
        requireActiveAdmin(adminId);
        Project project = requireProject(projectId);
        projectAccessService.requireActive(project);
        Request task = findTask(projectId, taskId);
        User assignee = projectAccessService.requireEligibleAssignee(project, input.assignedToId());
        if (task.getAssignedTo() == null || !task.getAssignedTo().getId().equals(assignee.getId())) {
            task.assignTo(assignee);
            requestRepository.save(task);
        }
        return adminDetails(task);
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
        return ProjectTaskDetailsResponse.from(task, timeline, comments);
    }

    private AdminProjectTaskDetailsResponse adminDetails(Request task) {
        var timeline = statusHistoryRepository.findByRequest_IdOrderByChangedAtAscIdAsc(task.getId()).stream()
                .map(RequestDetailsResponse.TimelineEntry::from).toList();
        var comments = commentRepository.findByRequest_IdAndInternalFalseOrderByCreatedAtAscIdAsc(task.getId())
                .stream().map(CommentResponse::from).toList();
        var internalNotes = commentRepository.findByRequest_IdAndInternalTrueOrderByCreatedAtAscIdAsc(task.getId())
                .stream().map(CommentResponse::from).toList();
        return AdminProjectTaskDetailsResponse.from(task, timeline, comments, internalNotes);
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
