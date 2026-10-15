package com.requestmanagement.backend.guestform;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface ProjectGuestFieldOptionRepository extends JpaRepository<ProjectGuestFieldOption, Long> {
    List<ProjectGuestFieldOption> findByField_IdOrderByDisplayOrderAscIdAsc(Long fieldId);
    List<ProjectGuestFieldOption> findByField_IdAndActiveTrueOrderByDisplayOrderAscIdAsc(Long fieldId);
    Optional<ProjectGuestFieldOption> findByIdAndField_IdAndActiveTrue(Long id, Long fieldId);
    void deleteAllByField_Project_Id(Long projectId);
}
