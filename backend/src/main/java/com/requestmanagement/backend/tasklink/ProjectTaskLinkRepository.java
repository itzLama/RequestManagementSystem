package com.requestmanagement.backend.tasklink;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectTaskLinkRepository extends JpaRepository<ProjectTaskLink, Long> {
    @Query("""
            select l from ProjectTaskLink l
            join fetch l.sourceTask s join fetch l.targetTask t
            where l.project.id = :projectId and (s.id = :taskId or t.id = :taskId)
            order by l.createdAt asc, l.id asc
            """)
    List<ProjectTaskLink> findForTask(@Param("projectId") Long projectId, @Param("taskId") Long taskId);

    List<ProjectTaskLink> findByProject_IdAndRelationshipType(
            Long projectId, ProjectTaskLink.RelationshipType relationshipType);

    Optional<ProjectTaskLink> findByIdAndProject_Id(Long id, Long projectId);

    boolean existsByProject_IdAndSourceTask_IdAndTargetTask_IdAndRelationshipType(
            Long projectId, Long sourceId, Long targetId, ProjectTaskLink.RelationshipType type);

    @Query("""
            select count(l) > 0 from ProjectTaskLink l
            where l.project.id = :projectId and l.relationshipType = :type
              and ((l.sourceTask.id = :firstId and l.targetTask.id = :secondId)
                or (l.sourceTask.id = :secondId and l.targetTask.id = :firstId))
            """)
    boolean existsLogicalPair(@Param("projectId") Long projectId,
                              @Param("firstId") Long firstId,
                              @Param("secondId") Long secondId,
                              @Param("type") ProjectTaskLink.RelationshipType type);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ProjectTaskLink l where l.sourceTask.id in :taskIds or l.targetTask.id in :taskIds")
    void deleteByTaskIds(@Param("taskIds") Collection<Long> taskIds);
}
