package com.requestmanagement.backend.request;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestRepository extends JpaRepository<Request, Long> {
    Page<Request> findByCreatedBy_Id(Long creatorId, Pageable pageable);

    @EntityGraph(attributePaths = {"type", "createdBy", "assignedTo"})
    Optional<Request> findByIdAndCreatedBy_Id(Long id, Long creatorId);
}
