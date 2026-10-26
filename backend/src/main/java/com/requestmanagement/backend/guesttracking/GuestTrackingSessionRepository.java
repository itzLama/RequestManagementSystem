package com.requestmanagement.backend.guesttracking;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface GuestTrackingSessionRepository extends JpaRepository<GuestTrackingSession,UUID>{
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from GuestTrackingSession s join fetch s.request r left join fetch r.project left join fetch r.workType left join fetch r.projectStatus left join fetch r.type where s.tokenDigest=:digest") Optional<GuestTrackingSession> findLockedByTokenDigest(@Param("digest") String digest);
 void deleteByRequest_IdIn(Collection<Long> requestIds);
}
