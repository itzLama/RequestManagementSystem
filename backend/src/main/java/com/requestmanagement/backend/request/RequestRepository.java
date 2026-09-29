package com.requestmanagement.backend.request;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RequestRepository extends JpaRepository<Request, Long>, JpaSpecificationExecutor<Request> {
    @EntityGraph(attributePaths = "type")
    Page<Request> findByCreatedBy_Id(Long creatorId, Pageable pageable);

    @EntityGraph(attributePaths = "type")
    List<Request> findByCreatedBy_IdOrderByCreatedAtDescIdDesc(Long creatorId);

    @EntityGraph(attributePaths = {"type", "createdBy"})
    Page<Request> findAll(org.springframework.data.jpa.domain.Specification<Request> specification,
                          Pageable pageable);

    @EntityGraph(attributePaths = {"type", "createdBy"})
    List<Request> findAll(org.springframework.data.jpa.domain.Specification<Request> specification,
                          org.springframework.data.domain.Sort sort);

    @EntityGraph(attributePaths = {"type", "createdBy", "assignedTo"})
    Optional<Request> findByIdAndCreatedBy_Id(Long id, Long creatorId);

    @EntityGraph(attributePaths = {"type", "createdBy", "assignedTo"})
    Optional<Request> findAdminDetailsById(Long id);

}
