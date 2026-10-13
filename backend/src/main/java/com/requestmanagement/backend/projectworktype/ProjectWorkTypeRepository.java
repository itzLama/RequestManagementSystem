package com.requestmanagement.backend.projectworktype;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectWorkTypeRepository extends JpaRepository<ProjectWorkType, Long> {
    List<ProjectWorkType> findByProject_IdOrderByActiveDescNameAscIdAsc(Long projectId);
    List<ProjectWorkType> findByProject_IdAndActiveTrueOrderByNameAscIdAsc(Long projectId);
    Optional<ProjectWorkType> findByIdAndProject_Id(Long id, Long projectId);
    Optional<ProjectWorkType> findByIdAndProject_IdAndActiveTrue(Long id, Long projectId);
    boolean existsByProject_IdAndNameIgnoreCase(Long projectId, String name);
    boolean existsByProject_IdAndNameIgnoreCaseAndIdNot(Long projectId, String name, Long id);
    long countByProject_IdAndActiveTrue(Long projectId);
    void deleteAllByProject_Id(Long projectId);
}
