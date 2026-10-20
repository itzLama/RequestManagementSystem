package com.requestmanagement.backend.guesttracking;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;
public interface GuestTrackingChallengeRepository extends JpaRepository<GuestTrackingChallenge,UUID>{
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from GuestTrackingChallenge c left join fetch c.request where c.id=:id") Optional<GuestTrackingChallenge> findLocked(@Param("id") UUID id);
 long countByLookupDigestAndCreatedAtAfter(String digest,LocalDateTime after);
 List<GuestTrackingChallenge> findByRequest_IdAndConsumedAtIsNullAndSupersededAtIsNull(Long requestId);
}
