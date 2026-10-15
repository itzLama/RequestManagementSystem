package com.requestmanagement.backend.guestform;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface ProjectGuestFieldRepository extends JpaRepository<ProjectGuestField, Long> {
    List<ProjectGuestField> findByProject_IdOrderByDisplayOrderAscIdAsc(Long projectId);
    List<ProjectGuestField> findByProject_IdAndActiveTrueOrderByDisplayOrderAscIdAsc(Long projectId);
    Optional<ProjectGuestField> findByIdAndProject_Id(Long id, Long projectId);
    boolean existsByProject_IdAndLabelIgnoreCase(Long projectId, String label);
    boolean existsByProject_IdAndLabelIgnoreCaseAndIdNot(Long projectId, String label, Long id);
    long countByProject_IdAndActiveTrue(Long projectId);
    void deleteAllByProject_Id(Long projectId);
}
