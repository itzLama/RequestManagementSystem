package com.requestmanagement.backend.comment;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    @EntityGraph(attributePaths = "user")
    List<Comment> findByRequest_IdAndInternalFalseOrderByCreatedAtAscIdAsc(Long requestId);
}
