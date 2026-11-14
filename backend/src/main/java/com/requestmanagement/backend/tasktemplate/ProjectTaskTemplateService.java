package com.requestmanagement.backend.tasktemplate;

import com.requestmanagement.backend.project.Project;
import com.requestmanagement.backend.project.ProjectAccessService;
import com.requestmanagement.backend.project.ProjectRepository;
import com.requestmanagement.backend.projectworktype.ProjectWorkType;
import com.requestmanagement.backend.projectworktype.ProjectWorkTypeService;
import com.requestmanagement.backend.request.RichDescriptionValidator;
import com.requestmanagement.backend.user.User;
import com.requestmanagement.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Admin-managed, per-Project Task Templates. Templates only pre-fill the normal Task creation
 * dialog; Task creation itself always goes through ProjectTaskService and its authorization.
 */
@Service
@RequiredArgsConstructor
public class ProjectTaskTemplateService {
    private final ProjectTaskTemplateRepository repository;
    private final ProjectRepository projectRepository;
    private final ProjectAccessService projectAccessService;
    private final ProjectWorkTypeService projectWorkTypeService;
    private final RichDescriptionValidator richDescriptionValidator;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ProjectTaskTemplateDtos.Response> listAdmin(Long projectId, Long adminId) {
        requireActiveAdmin(adminId);
        requireProject(projectId);
        return list(projectId);
    }

    @Transactional(readOnly = true)
    public List<ProjectTaskTemplateDtos.Response> listEmployee(Long projectId, Long employeeId) {
        projectAccessService.requireAccessibleProject(projectId, employeeId);
        return list(projectId);
    }

    @Transactional
    public ProjectTaskTemplateDtos.Response create(Long projectId, Long adminId, ProjectTaskTemplateDtos.Input input) {
        User admin = requireActiveAdmin(adminId);
        Project project = requireMutableProject(projectId);
        ProjectTaskTemplate.Values values = values(projectId, input);
        requireUniqueNames(projectId, values, null);
        try {
            return ProjectTaskTemplateDtos.Response.from(
                    repository.saveAndFlush(ProjectTaskTemplate.create(project, admin, values)));
        } catch (DataIntegrityViolationException exception) {
            throw duplicate(exception);
        }
    }

    @Transactional
    public ProjectTaskTemplateDtos.Response update(Long projectId, Long templateId, Long adminId,
                                                   ProjectTaskTemplateDtos.Input input) {
        requireActiveAdmin(adminId);
        requireMutableProject(projectId);
        ProjectTaskTemplate template = requireOwned(projectId, templateId);
        ProjectTaskTemplate.Values values = values(projectId, input);
        requireUniqueNames(projectId, values, templateId);
        template.update(values);
        try {
            return ProjectTaskTemplateDtos.Response.from(repository.saveAndFlush(template));
        } catch (DataIntegrityViolationException exception) {
            throw duplicate(exception);
        }
    }

    @Transactional
    public void delete(Long projectId, Long templateId, Long adminId) {
        requireActiveAdmin(adminId);
        requireMutableProject(projectId);
        ProjectTaskTemplate template = requireOwned(projectId, templateId);
        if (repository.countRecurringRules(templateId) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This Task Template is used by a Recurring Task rule. Delete or change that rule first.");
        }
        repository.delete(template);
        repository.flush();
    }

    private List<ProjectTaskTemplateDtos.Response> list(Long projectId) {
        return repository.findByProject_IdOrderByNameAscIdAsc(projectId).stream()
                .map(ProjectTaskTemplateDtos.Response::from).toList();
    }

    private ProjectTaskTemplate.Values values(Long projectId, ProjectTaskTemplateDtos.Input input) {
        String name = required(input.name(), "Template name is required.");
        String nameAr = required(input.nameAr(), "Arabic template name is required.");
        String title = required(input.defaultTitle(), "Default title is required.");
        ProjectWorkType workType = projectWorkTypeService.requireActiveForProject(projectId, input.workTypeId());
        RichDescriptionValidator.ValidatedDocument description = richDescriptionValidator.validate(input.descriptionDocument());
        return new ProjectTaskTemplate.Values(name, nameAr, workType, input.priority(), title,
                description.plainText(), description.document(), input.dueOffsetDays());
    }

    private void requireUniqueNames(Long projectId, ProjectTaskTemplate.Values values, Long excludedId) {
        boolean english = excludedId == null
                ? repository.existsByProject_IdAndNameIgnoreCase(projectId, values.name())
                : repository.existsByProject_IdAndNameIgnoreCaseAndIdNot(projectId, values.name(), excludedId);
        boolean arabic = excludedId == null
                ? repository.existsByProject_IdAndNameArIgnoreCase(projectId, values.nameAr())
                : repository.existsByProject_IdAndNameArIgnoreCaseAndIdNot(projectId, values.nameAr(), excludedId);
        if (english || arabic) throw duplicate(null);
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        return value.trim();
    }

    private ProjectTaskTemplate requireOwned(Long projectId, Long templateId) {
        return repository.findByIdAndProject_Id(templateId, projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task Template not found."));
    }

    private Project requireProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found."));
    }

    private Project requireMutableProject(Long projectId) {
        Project project = requireProject(projectId);
        if (!project.isActive()) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Archived projects cannot be modified.");
        return project;
    }

    private User requireActiveAdmin(Long adminId) {
        return userRepository.findById(adminId)
                .filter(User::isActive)
                .filter(user -> user.getRole() == User.Role.ADMIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Administrator access is required."));
    }

    private ResponseStatusException duplicate(Throwable cause) {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "A Task Template with this name already exists in the Project.", cause);
    }
}
