package com.requestmanagement.backend.request;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;

public interface RequestRepository extends JpaRepository<Request, Long>, JpaSpecificationExecutor<Request> {
    boolean existsByCreatedBy_Id(Long creatorId);

    boolean existsByAssignedTo_Id(Long assigneeId);

    boolean existsByProject_Id(Long projectId);
    boolean existsByIdAndProject_Id(Long id, Long projectId);
    boolean existsByWorkType_Id(Long workTypeId);
    long countByProjectStatus_Id(Long statusId);
    List<Request> findByProjectStatus_Id(Long statusId);
    boolean existsByParentTask_Id(Long parentTaskId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Request r set r.attachmentCleanupStartedAt=coalesce(r.attachmentCleanupStartedAt,current_timestamp) where r.id in :ids")
    int markAttachmentCleanupStarted(@Param("ids") List<Long> ids);

    @Query("select count(r)>0 from Request r where r.id=:id and r.attachmentCleanupStartedAt is not null")
    boolean isAttachmentCleanupStarted(@Param("id") Long id);
    long countByParentTask_Id(Long parentTaskId);

    @EntityGraph(attributePaths = {"workType", "projectStatus", "assignedTo", "parentTask"})
    List<Request> findByParentTask_IdOrderByCreatedAtAscIdAsc(Long parentTaskId);

    @Query(value = "SELECT * FROM requests WHERE request_id=:taskId AND project_id=:projectId FOR UPDATE",
            nativeQuery = true)
    Optional<Request> findForDeletion(@Param("projectId") Long projectId, @Param("taskId") Long taskId);

    @Query(value = "SELECT * FROM requests WHERE request_id=:taskId AND project_id=:projectId FOR UPDATE",
            nativeQuery = true)
    Optional<Request> findForAttachmentReservation(@Param("projectId") Long projectId,
                                                   @Param("taskId") Long taskId);

    @Query(value = "SELECT * FROM requests WHERE parent_request_id=:parentId AND project_id=:projectId " +
            "ORDER BY request_id FOR UPDATE", nativeQuery = true)
    List<Request> findSubtasksForDeletion(@Param("projectId") Long projectId, @Param("parentId") Long parentId);

    @EntityGraph(attributePaths = "type")
    Page<Request> findByCreatedBy_IdAndProjectIsNull(Long creatorId, Pageable pageable);

    @EntityGraph(attributePaths = "type")
    List<Request> findByCreatedBy_IdAndProjectIsNullOrderByCreatedAtDescIdDesc(Long creatorId);

    @EntityGraph(attributePaths = {"type", "assignedTo", "createdBy"})
    Page<Request> findByAssignedTo_IdAndProjectIsNull(Long assigneeId, Pageable pageable);

    @EntityGraph(attributePaths = {"type", "assignedTo", "createdBy"})
    List<Request> findByAssignedTo_IdAndProjectIsNullOrderByCreatedAtDescIdDesc(Long assigneeId);

    @EntityGraph(attributePaths = {"type", "createdBy", "project", "assignedTo"})
    Page<Request> findAll(org.springframework.data.jpa.domain.Specification<Request> specification,
                          Pageable pageable);

    @EntityGraph(attributePaths = {"type", "createdBy", "project", "assignedTo"})
    List<Request> findAll(org.springframework.data.jpa.domain.Specification<Request> specification,
                          org.springframework.data.domain.Sort sort);

    @EntityGraph(attributePaths = {"type", "createdBy", "assignedTo"})
    Optional<Request> findByIdAndCreatedBy_IdAndProjectIsNull(Long id, Long creatorId);

    @EntityGraph(attributePaths = {"type", "createdBy", "assignedTo"})
    Optional<Request> findByIdAndAssignedTo_IdAndProjectIsNull(Long id, Long assigneeId);

    @EntityGraph(attributePaths = {"type", "createdBy", "assignedTo"})
    List<Request> findByProject_IdAndParentTaskIsNullOrderByCreatedAtDescIdDesc(Long projectId);

    @EntityGraph(attributePaths = {"projectStatus", "parentTask"})
    List<Request> findByProject_IdOrderByCreatedAtDescIdDesc(Long projectId);

    @EntityGraph(attributePaths = {"type", "createdBy", "assignedTo", "project", "parentTask"})
    Optional<Request> findByIdAndProject_Id(Long id, Long projectId);

    @EntityGraph(attributePaths = {"type", "project", "workType", "projectStatus"})
    Optional<Request> findByIdAndGuestEmailIgnoreCaseAndCreatedByIsNull(Long id, String guestEmail);

    @EntityGraph(attributePaths = {"type", "createdBy", "assignedTo", "project"})
    Optional<Request> findAdminDetailsByIdAndProjectIsNull(Long id);

    @Query("""
            SELECT r.status AS status, COUNT(r) AS requestCount
            FROM Request r
            WHERE r.project IS NULL
            GROUP BY r.status
            """)
    List<RequestStatusCountProjection> countRequestsByStatus();

    @EntityGraph(attributePaths = "type")
    List<Request> findTop5ByProjectIsNullOrderByCreatedAtDescIdDesc();

}
