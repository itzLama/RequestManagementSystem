package com.requestmanagement.backend.project;

import com.requestmanagement.backend.request.Request;
import com.requestmanagement.backend.user.User;
import com.requestmanagement.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ProjectAccessService {
    private final ProjectMembershipRepository membershipRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public User requireActiveEmployee(Long employeeId) {
        return userRepository.findByIdAndRole(employeeId, User.Role.EMPLOYEE)
                .filter(User::isActive)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Active Employee access is required."));
    }

    @Transactional(readOnly = true)
    public Project requireAccessibleProject(Long projectId, Long employeeId) {
        requireActiveEmployee(employeeId);
        return membershipRepository.findWithProjectByProject_IdAndEmployee_Id(projectId, employeeId)
                .map(ProjectMembership::getProject)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found."));
    }

    @Transactional(readOnly = true)
    public Project requireMutableProject(Long projectId, Long employeeId) {
        Project project = requireAccessibleProject(projectId, employeeId);
        requireActive(project);
        return project;
    }

    @Transactional(readOnly = true)
    public User requireEligibleAssignee(Project project, Long assigneeId) {
        User assignee = userRepository.findByIdAndRole(assigneeId, User.Role.EMPLOYEE)
                .filter(User::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Assignee must be an active Project member."));
        if (!membershipRepository.existsByProject_IdAndEmployee_Id(project.getId(), assigneeId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Assignee must be an active Project member.");
        }
        return assignee;
    }

    public void requireMutableIfProjectTask(Request request) {
        if (request.isProjectTask()) requireActive(request.getProject());
    }

    public void requireActive(Project project) {
        if (!project.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived projects are read-only.");
        }
    }
}
