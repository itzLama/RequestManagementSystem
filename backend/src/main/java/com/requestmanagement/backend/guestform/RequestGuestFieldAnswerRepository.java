package com.requestmanagement.backend.guestform;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
public interface RequestGuestFieldAnswerRepository extends JpaRepository<RequestGuestFieldAnswer, Long> {
    List<RequestGuestFieldAnswer> findByRequest_IdOrderByDisplayOrderAscIdAsc(Long requestId);
    boolean existsByField_Id(Long fieldId);
    void deleteByRequest_IdIn(Collection<Long> requestIds);
}
