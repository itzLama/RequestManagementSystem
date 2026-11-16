package com.requestmanagement.backend.projectworktype;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectWorkTypeRepository extends JpaRepository<ProjectWorkType, Long> {
    List<ProjectWorkType> findByProject_IdOrderByActiveDescNameAscIdAsc(Long projectId);
    List<ProjectWorkType> findByProject_IdAndActiveTrueOrderByNameAscIdAsc(Long projectId);
    Optional<ProjectWorkType> findByIdAndProject_Id(Long id, Long projectId);
    Optional<ProjectWorkType> findByIdAndProject_IdAndActiveTrue(Long id, Long projectId);
    List<ProjectWorkType> findByProject_IdAndNameIgnoreCase(Long projectId, String name);
    List<ProjectWorkType> findByProject_IdAndNameArIgnoreCase(Long projectId, String nameAr);
    long countByProject_IdAndActiveTrue(Long projectId);
    void deleteAllByProject_Id(Long projectId);
}
