package com.requestmanagement.backend.project;

import com.requestmanagement.backend.request.RequestRepository;
import com.requestmanagement.backend.projectworktype.ProjectWorkTypeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectServiceTest {
    @Test
    void databaseIntegrityConflictIsReportedAsSafeConflict() {
        ProjectRepository projects = mock(ProjectRepository.class);
        Project project = mock(Project.class);
        when(projects.findById(42L)).thenReturn(Optional.of(project));
        doThrow(new DataIntegrityViolationException("Concurrent dependent record"))
                .when(projects).flush();

        ProjectService service = new ProjectService(
                projects,
                mock(ProjectMembershipRepository.class),
                mock(RequestRepository.class),
                mock(ProjectWorkTypeRepository.class));

        assertThatThrownBy(() -> service.delete(42L))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason())
                            .isEqualTo("Project has persisted activity and must be archived instead.");
                });
    }
}
