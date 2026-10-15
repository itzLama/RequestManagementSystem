package com.requestmanagement.backend.guestform;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProjectGuestFormSettingsRepository extends JpaRepository<ProjectGuestFormSettings, Long> {
    boolean existsByDefaultWorkType_IdAndWorkTypeVisibleFalse(Long workTypeId);
}
