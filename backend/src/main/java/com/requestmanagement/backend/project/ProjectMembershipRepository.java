package com.requestmanagement.backend.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectMembershipRepository extends JpaRepository<ProjectMembership, Long> {
    List<ProjectMembership> findByProject_IdOrderByEmployee_FullNameAscEmployee_IdAsc(Long projectId);
    Optional<ProjectMembership> findByProject_IdAndEmployee_Id(Long projectId, Long employeeId);
    boolean existsByProject_IdAndEmployee_Id(Long projectId, Long employeeId);
}
