package com.requestmanagement.backend.project;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeeProjectService {
    private final ProjectAccessService projectAccessService;
    private final ProjectMembershipRepository membershipRepository;

    @Transactional(readOnly = true)
    public List<ProjectResponse> listMine(Long employeeId) {
        projectAccessService.requireActiveEmployee(employeeId);
        return membershipRepository.findByEmployee_IdOrderByProject_CreatedAtDescProject_IdDesc(employeeId)
                .stream().map(ProjectMembership::getProject).map(ProjectResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse details(Long projectId, Long employeeId) {
        return ProjectResponse.from(projectAccessService.requireAccessibleProject(projectId, employeeId));
    }

    @Transactional(readOnly = true)
    public List<ProjectTaskMemberResponse> members(Long projectId, Long employeeId) {
        projectAccessService.requireAccessibleProject(projectId, employeeId);
        return membershipRepository
                .findByProject_IdOrderByEmployee_FullNameAscEmployee_IdAsc(projectId)
                .stream().map(ProjectTaskMemberResponse::from).toList();
    }
}
