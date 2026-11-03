package com.requestmanagement.backend.projectworktype;

import com.requestmanagement.backend.project.Project;
import com.requestmanagement.backend.project.ProjectAccessService;
import com.requestmanagement.backend.project.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import com.requestmanagement.backend.guestform.ProjectGuestFormSettingsRepository;
import com.requestmanagement.backend.request.RequestRepository;

@Service
@RequiredArgsConstructor
public class ProjectWorkTypeService {
    private final ProjectWorkTypeRepository repository;
    private final ProjectRepository projectRepository;
    private final ProjectAccessService projectAccessService;
    private final ProjectGuestFormSettingsRepository guestFormSettingsRepository;
    private final RequestRepository requestRepository;

    @Transactional(readOnly = true)
    public List<ProjectWorkTypeResponse> listAdmin(Long projectId) {
        requireProject(projectId);
        return repository.findByProject_IdOrderByActiveDescNameAscIdAsc(projectId).stream()
                .map(ProjectWorkTypeResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ProjectWorkTypeResponse> listEmployee(Long projectId, Long employeeId) {
        projectAccessService.requireAccessibleProject(projectId, employeeId);
        return active(projectId);
    }

    @Transactional(readOnly = true)
    public List<ProjectWorkTypeResponse> listGuest(Long projectId) {
        requireActiveProject(projectId);
        return active(projectId);
    }

    @Transactional
    public ProjectWorkTypeResponse create(Long projectId, ProjectWorkTypeNameRequest input) {
        Project project = requireActiveProject(projectId);
        String name = normalize(input.name());
        String nameAr = normalizeArabic(input.nameAr());
        requireUnique(projectId, name, null);
        requireUniqueArabic(projectId, nameAr, null);
        try {
            return ProjectWorkTypeResponse.from(repository.saveAndFlush(ProjectWorkType.create(project, name, nameAr)));
        } catch (DataIntegrityViolationException exception) {
            throw duplicate(exception);
        }
    }

    @Transactional
    public ProjectWorkTypeResponse rename(Long projectId, Long workTypeId, ProjectWorkTypeNameRequest input) {
        requireActiveProject(projectId);
        ProjectWorkType type = requireOwned(projectId, workTypeId);
        String name = normalize(input.name());
        String nameAr = normalizeArabic(input.nameAr());
        requireUnique(projectId, name, workTypeId);
        requireUniqueArabic(projectId, nameAr, workTypeId);
        type.rename(name, nameAr);
        try {
            return ProjectWorkTypeResponse.from(repository.saveAndFlush(type));
        } catch (DataIntegrityViolationException exception) {
            throw duplicate(exception);
        }
    }

    @Transactional
    public void retire(Long projectId, Long workTypeId) {
        requireActiveProject(projectId);
        ProjectWorkType type = requireOwned(projectId, workTypeId);
        if (!type.isActive()) return;
        if (guestFormSettingsRepository.existsByDefaultWorkType_IdAndWorkTypeVisibleFalse(workTypeId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This Work Type is the hidden Guest form default. Change the default or show Work Type before retiring it.");
        }
        if (repository.countByProject_IdAndActiveTrue(projectId) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A Project must keep at least one active Work Type.");
        }
        if (requestRepository.existsByWorkType_Id(workTypeId)) {
            type.retire();
            repository.saveAndFlush(type);
        } else {
            repository.delete(type);
            repository.flush();
        }
    }

    @Transactional(readOnly = true)
    public ProjectWorkType requireActiveForProject(Long projectId, Long workTypeId) {
        return repository.findByIdAndProject_IdAndActiveTrue(workTypeId, projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Work Type must exist, be active, and belong to this Project."));
    }

    private List<ProjectWorkTypeResponse> active(Long projectId) {
        return repository.findByProject_IdAndActiveTrueOrderByNameAscIdAsc(projectId).stream()
                .map(ProjectWorkTypeResponse::from).toList();
    }

    private Project requireProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found."));
    }

    private Project requireActiveProject(Long projectId) {
        Project project = requireProject(projectId);
        if (!project.isActive()) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Archived projects cannot be modified.");
        return project;
    }

    private ProjectWorkType requireOwned(Long projectId, Long workTypeId) {
        return repository.findByIdAndProject_Id(workTypeId, projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work Type not found."));
    }

    private String normalize(String value) {
        String name = value.trim();
        if (name.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Work Type name is required.");
        return name;
    }

    private String normalizeArabic(String value) {
        if (value == null || value.isBlank()) return null;
        String name = value.trim();
        if (name.length() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arabic Work Type name must be at most 100 characters.");
        return name;
    }

    private void requireUnique(Long projectId, String name, Long excludedId) {
        boolean exists = excludedId == null
                ? repository.existsByProject_IdAndNameIgnoreCase(projectId, name)
                : repository.existsByProject_IdAndNameIgnoreCaseAndIdNot(projectId, name, excludedId);
        if (exists) throw duplicate(null);
    }

    private void requireUniqueArabic(Long projectId, String nameAr, Long excludedId) {
        if (nameAr == null) return;
        boolean exists = excludedId == null
                ? repository.existsByProject_IdAndNameArIgnoreCase(projectId, nameAr)
                : repository.existsByProject_IdAndNameArIgnoreCaseAndIdNot(projectId, nameAr, excludedId);
        if (exists) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "A Work Type with this Arabic name already exists in the Project.");
    }

    private ResponseStatusException duplicate(Throwable cause) {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "A Work Type with this name already exists in the Project.", cause);
    }
}
