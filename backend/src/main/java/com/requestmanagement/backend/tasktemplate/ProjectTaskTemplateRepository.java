package com.requestmanagement.backend.tasktemplate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectTaskTemplateRepository extends JpaRepository<ProjectTaskTemplate, Long> {
    List<ProjectTaskTemplate> findByProject_IdOrderByNameAscIdAsc(Long projectId);

    Optional<ProjectTaskTemplate> findByIdAndProject_Id(Long id, Long projectId);

    boolean existsByProject_IdAndNameIgnoreCase(Long projectId, String name);

    boolean existsByProject_IdAndNameIgnoreCaseAndIdNot(Long projectId, String name, Long id);

    boolean existsByProject_IdAndNameArIgnoreCase(Long projectId, String nameAr);

    boolean existsByProject_IdAndNameArIgnoreCaseAndIdNot(Long projectId, String nameAr, Long id);

    boolean existsByWorkType_Id(Long workTypeId);

    void deleteAllByProject_Id(Long projectId);

    @Query(value = "SELECT COUNT(*) FROM project_recurring_task_rules WHERE template_id = :templateId", nativeQuery = true)
    long countRecurringRules(@Param("templateId") Long templateId);
}
