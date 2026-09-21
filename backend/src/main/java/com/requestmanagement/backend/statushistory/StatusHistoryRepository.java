package com.requestmanagement.backend.statushistory;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {
    @EntityGraph(attributePaths = "changedBy")
    List<StatusHistory> findByRequest_IdOrderByChangedAtAscIdAsc(Long requestId);
}
