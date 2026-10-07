package com.requestmanagement.backend.request;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface RequestRepository extends JpaRepository<Request, Long>, JpaSpecificationExecutor<Request> {
    @EntityGraph(attributePaths = "type")
    Page<Request> findByCreatedBy_IdAndProjectIsNull(Long creatorId, Pageable pageable);

    @EntityGraph(attributePaths = "type")
    List<Request> findByCreatedBy_IdAndProjectIsNullOrderByCreatedAtDescIdDesc(Long creatorId);

    @EntityGraph(attributePaths = "type")
    Page<Request> findByAssignedTo_IdAndProjectIsNull(Long assigneeId, Pageable pageable);

    @EntityGraph(attributePaths = "type")
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
    List<Request> findByProject_IdOrderByCreatedAtDescIdDesc(Long projectId);

    @EntityGraph(attributePaths = {"type", "createdBy", "assignedTo", "project"})
    Optional<Request> findByIdAndProject_Id(Long id, Long projectId);

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
