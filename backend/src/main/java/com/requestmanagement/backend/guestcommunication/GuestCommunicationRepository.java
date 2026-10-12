package com.requestmanagement.backend.guestcommunication;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuestCommunicationRepository extends JpaRepository<GuestCommunication, Long> {
    boolean existsBySentBy_Id(Long userId);

    @EntityGraph(attributePaths = "sentBy")
    List<GuestCommunication> findByRequest_IdOrderBySentAtAscIdAsc(Long requestId);
}
