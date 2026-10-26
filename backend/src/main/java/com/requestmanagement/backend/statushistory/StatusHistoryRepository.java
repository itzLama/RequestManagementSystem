package com.requestmanagement.backend.statushistory;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {
    boolean existsByChangedBy_Id(Long userId);

    @EntityGraph(attributePaths = "changedBy")
    List<StatusHistory> findByRequest_IdOrderByChangedAtAscIdAsc(Long requestId);

    List<StatusHistory> findByOldProjectStatus_IdOrNewProjectStatus_Id(Long oldStatusId, Long newStatusId);
    void deleteByRequest_IdIn(Collection<Long> requestIds);
}
