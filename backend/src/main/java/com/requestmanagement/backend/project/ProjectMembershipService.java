package com.requestmanagement.backend.project;

import com.requestmanagement.backend.user.User;
import com.requestmanagement.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectMembershipService {
    private final ProjectService projectService;
    private final ProjectMembershipRepository membershipRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> list(Long projectId) {
        projectService.requireProject(projectId);
        return membershipRepository.findByProject_IdOrderByEmployee_FullNameAscEmployee_IdAsc(projectId)
                .stream().map(ProjectMemberResponse::from).toList();
    }

    @Transactional
    public ProjectMemberResponse add(Long projectId, AddProjectMemberRequest input) {
        Project project = projectService.requireActiveProject(projectId);
        User employee = userRepository.findByIdAndRole(input.employeeId(), User.Role.EMPLOYEE)
                .filter(User::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Project member must be an active employee."));
        if (membershipRepository.existsByProject_IdAndEmployee_Id(projectId, employee.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Employee is already a project member.");
        }
        try {
            return ProjectMemberResponse.from(membershipRepository.saveAndFlush(
                    ProjectMembership.create(project, employee)));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Employee is already a project member.", exception);
        }
    }

    @Transactional
    public void remove(Long projectId, Long employeeId) {
        projectService.requireActiveProject(projectId);
        ProjectMembership membership = membershipRepository.findByProject_IdAndEmployee_Id(projectId, employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project membership not found."));
        membershipRepository.delete(membership);
    }
}
