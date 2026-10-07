package com.requestmanagement.backend.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface ProjectMembershipRepository extends JpaRepository<ProjectMembership, Long> {
    List<ProjectMembership> findByProject_IdOrderByEmployee_FullNameAscEmployee_IdAsc(Long projectId);
    List<ProjectMembership> findByProject_IdAndEmployee_ActiveTrueAndEmployee_RoleOrderByEmployee_FullNameAscEmployee_IdAsc(
            Long projectId, com.requestmanagement.backend.user.User.Role role);
    Optional<ProjectMembership> findByProject_IdAndEmployee_Id(Long projectId, Long employeeId);
    boolean existsByProject_IdAndEmployee_Id(Long projectId, Long employeeId);

    @EntityGraph(attributePaths = "project")
    List<ProjectMembership> findByEmployee_IdOrderByProject_CreatedAtDescProject_IdDesc(Long employeeId);

    @EntityGraph(attributePaths = "project")
    Optional<ProjectMembership> findWithProjectByProject_IdAndEmployee_Id(Long projectId, Long employeeId);
}
