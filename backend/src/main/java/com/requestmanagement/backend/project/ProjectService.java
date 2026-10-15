package com.requestmanagement.backend.project;

import com.requestmanagement.backend.request.RequestRepository;
import com.requestmanagement.backend.projectworktype.ProjectWorkType;
import com.requestmanagement.backend.projectworktype.ProjectWorkTypeRepository;
import com.requestmanagement.backend.guestform.ProjectGuestFormSettings;
import com.requestmanagement.backend.guestform.ProjectGuestFormSettingsRepository;
import com.requestmanagement.backend.guestform.ProjectGuestFieldRepository;
import com.requestmanagement.backend.guestform.ProjectGuestFieldOptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final ProjectMembershipRepository projectMembershipRepository;
    private final RequestRepository requestRepository;
    private final ProjectWorkTypeRepository projectWorkTypeRepository;
    private final ProjectGuestFormSettingsRepository guestFormSettingsRepository;
    private final ProjectGuestFieldRepository guestFieldRepository;
    private final ProjectGuestFieldOptionRepository guestFieldOptionRepository;

    @Transactional(readOnly = true)
    public List<ProjectResponse> list() {
        return projectRepository.findAllByOrderByCreatedAtDescIdDesc().stream().map(ProjectResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse details(Long id) {
        return ProjectResponse.from(requireProject(id));
    }

    @Transactional
    public ProjectResponse create(CreateProjectRequest input) {
        Project project = projectRepository.save(Project.create(
                normalizeName(input.name()), normalizeDescription(input.description())));
        projectWorkTypeRepository.saveAll(List.of("Task", "Bug", "Improvement").stream()
                .map(name -> ProjectWorkType.create(project, name)).toList());
        guestFormSettingsRepository.save(ProjectGuestFormSettings.defaults(project));
        return ProjectResponse.from(project);
    }

    @Transactional
    public ProjectResponse update(Long id, UpdateProjectRequest input) {
        Project project = requireActiveProject(id);
        project.update(normalizeName(input.name()), normalizeDescription(input.description()));
        return ProjectResponse.from(projectRepository.save(project));
    }

    @Transactional
    public ProjectResponse archive(Long id) {
        Project project = requireProject(id);
        project.archive();
        return ProjectResponse.from(projectRepository.save(project));
    }

    @Transactional
    public void delete(Long id) {
        Project project = requireProject(id);
        if (projectMembershipRepository.existsByProject_Id(id)
                || requestRepository.existsByProject_Id(id)) {
            throw deletionConflict();
        }

        try {
            guestFieldOptionRepository.deleteAllByField_Project_Id(id);
            guestFieldOptionRepository.flush();
            guestFieldRepository.deleteAllByProject_Id(id);
            guestFieldRepository.flush();
            guestFormSettingsRepository.deleteById(id);
            guestFormSettingsRepository.flush();
            projectWorkTypeRepository.deleteAllByProject_Id(id);
            projectWorkTypeRepository.flush();
            projectRepository.delete(project);
            projectRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Project has persisted activity and must be archived instead.",
                    exception);
        }
    }

    private ResponseStatusException deletionConflict() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Project has persisted activity and must be archived instead.");
    }

    Project requireProject(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found."));
    }

    Project requireActiveProject(Long id) {
        Project project = requireProject(id);
        if (!project.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived projects cannot be modified.");
        }
        return project;
    }

    private String normalizeName(String name) {
        String normalized = name.trim();
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project name is required.");
        }
        return normalized;
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) return null;
        return description.trim();
    }
}
