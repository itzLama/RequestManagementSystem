package com.requestmanagement.backend.guesttracking;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface GuestTrackingSessionRepository extends JpaRepository<GuestTrackingSession,UUID>{
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from GuestTrackingSession s join fetch s.request r join fetch r.project join fetch r.workType join fetch r.projectStatus where s.tokenDigest=:digest") Optional<GuestTrackingSession> findLockedByTokenDigest(@Param("digest") String digest);
}
