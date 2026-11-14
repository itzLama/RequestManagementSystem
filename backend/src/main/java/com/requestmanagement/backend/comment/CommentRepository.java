package com.requestmanagement.backend.comment;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    boolean existsByUser_Id(Long userId);

    @EntityGraph(attributePaths = "user")
    List<Comment> findByRequest_IdAndInternalFalseOrderByCreatedAtAscIdAsc(Long requestId);

    @EntityGraph(attributePaths = "user")
    List<Comment> findByRequest_IdAndInternalTrueOrderByCreatedAtAscIdAsc(Long requestId);
    void deleteByRequest_IdIn(Collection<Long> requestIds);

    /** Everyone who has commented on (or added an internal note to) the Request. */
    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT c.user.id FROM Comment c WHERE c.request.id = :requestId")
    List<Long> findDistinctAuthorIdsByRequestId(@org.springframework.data.repository.query.Param("requestId") Long requestId);
}
