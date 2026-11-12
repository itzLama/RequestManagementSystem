package com.requestmanagement.backend.tasklink;

import com.requestmanagement.backend.project.Project;
import com.requestmanagement.backend.project.ProjectAccessService;
import com.requestmanagement.backend.project.ProjectRepository;
import com.requestmanagement.backend.request.Request;
import com.requestmanagement.backend.request.RequestRepository;
import com.requestmanagement.backend.tasklink.ProjectTaskLink.RelationshipType;
import com.requestmanagement.backend.tasklink.ProjectTaskLinkDtos.*;
import com.requestmanagement.backend.user.User;
import com.requestmanagement.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ProjectTaskLinkService {
    private final ProjectTaskLinkRepository linkRepository;
    private final RequestRepository requestRepository;
    private final ProjectRepository projectRepository;
    private final ProjectAccessService projectAccessService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<Response> adminList(Long projectId, Long taskId, Long adminId) {
        requireActiveAdmin(adminId);
        requireProject(projectId);
        requireTask(projectId, taskId);
        return responses(projectId, taskId);
    }

    @Transactional(readOnly = true)
    public List<Response> employeeList(Long projectId, Long taskId, Long employeeId) {
        projectAccessService.requireAccessibleProject(projectId, employeeId);
        requireTask(projectId, taskId);
        return responses(projectId, taskId);
    }

    @Transactional(readOnly = true)
    public List<Candidate> adminCandidates(Long projectId, Long taskId, Long adminId) {
        requireActiveAdmin(adminId);
        requireProject(projectId);
        requireTask(projectId, taskId);
        return candidates(projectId, taskId);
    }

    @Transactional(readOnly = true)
    public List<Candidate> employeeCandidates(Long projectId, Long taskId, Long employeeId) {
        projectAccessService.requireAccessibleProject(projectId, employeeId);
        requireTask(projectId, taskId);
        return candidates(projectId, taskId);
    }

    @Transactional
    public Response adminCreate(Long projectId, Long taskId, Long adminId, CreateInput input) {
        return create(projectId, taskId, requireActiveAdmin(adminId), input, true);
    }

    @Transactional
    public Response employeeCreate(Long projectId, Long taskId, Long employeeId, CreateInput input) {
        projectAccessService.requireMutableProject(projectId, employeeId);
        return create(projectId, taskId, projectAccessService.requireActiveEmployee(employeeId), input, false);
    }

    @Transactional
    public void adminDelete(Long projectId, Long taskId, Long linkId, Long adminId) {
        requireActiveAdmin(adminId);
        delete(projectId, taskId, linkId, true, null);
    }

    @Transactional
    public void employeeDelete(Long projectId, Long taskId, Long linkId, Long employeeId) {
        projectAccessService.requireMutableProject(projectId, employeeId);
        delete(projectId, taskId, linkId, false, employeeId);
    }

    private Response create(Long projectId, Long currentTaskId, User actor, CreateInput input, boolean admin) {
        Project project = projectRepository.findByIdForTaskLinkUpdate(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found."));
        projectAccessService.requireActive(project);
        if (!admin) projectAccessService.requireAccessibleProject(projectId, actor.getId());
        Request current = requireTask(projectId, currentTaskId);
        Request related = requireTask(projectId, input.relatedTaskId());
        if (current.getId().equals(related.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A Task cannot be linked to itself.");
        }

        Canonical canonical = canonical(current, related, input.relationshipType());
        if (linkRepository.existsLogicalPair(projectId, canonical.source().getId(), canonical.target().getId(), canonical.type())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This Task relationship already exists.");
        }
        if (canonical.type() == RelationshipType.BLOCKS && createsBlockingCycle(projectId, canonical.source().getId(), canonical.target().getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This blocking relationship would create a cycle.");
        }
        try {
            ProjectTaskLink saved = linkRepository.saveAndFlush(ProjectTaskLink.create(
                    project, canonical.source(), canonical.target(), canonical.type(), actor));
            return response(saved, currentTaskId);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This Task relationship already exists.");
        }
    }

    private void delete(Long projectId, Long taskId, Long linkId, boolean admin, Long employeeId) {
        Project project = projectRepository.findByIdForTaskLinkUpdate(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found."));
        projectAccessService.requireActive(project);
        if (!admin) projectAccessService.requireAccessibleProject(projectId, employeeId);
        requireTask(projectId, taskId);
        ProjectTaskLink link = linkRepository.findByIdAndProject_Id(linkId, projectId)
                .filter(value -> value.getSourceTask().getId().equals(taskId) || value.getTargetTask().getId().equals(taskId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task relationship not found."));
        linkRepository.delete(link);
        linkRepository.flush();
    }

    private boolean createsBlockingCycle(Long projectId, Long sourceId, Long targetId) {
        Map<Long, List<Long>> graph = new HashMap<>();
        for (ProjectTaskLink link : linkRepository.findByProject_IdAndRelationshipType(projectId, RelationshipType.BLOCKS)) {
            graph.computeIfAbsent(link.getSourceTask().getId(), ignored -> new ArrayList<>())
                    .add(link.getTargetTask().getId());
        }
        graph.computeIfAbsent(sourceId, ignored -> new ArrayList<>()).add(targetId);
        return reachable(graph, targetId, sourceId, new HashSet<>());
    }

    private boolean reachable(Map<Long, List<Long>> graph, Long current, Long sought, Set<Long> visited) {
        if (current.equals(sought)) return true;
        if (!visited.add(current)) return false;
        return graph.getOrDefault(current, List.of()).stream()
                .anyMatch(next -> reachable(graph, next, sought, visited));
    }

    private Canonical canonical(Request current, Request related, PerspectiveType perspective) {
        return switch (perspective) {
            case BLOCKS -> new Canonical(current, related, RelationshipType.BLOCKS);
            case IS_BLOCKED_BY -> new Canonical(related, current, RelationshipType.BLOCKS);
            case DUPLICATES -> new Canonical(current, related, RelationshipType.DUPLICATES);
            case IS_DUPLICATED_BY -> new Canonical(related, current, RelationshipType.DUPLICATES);
            case RELATES_TO -> current.getId() < related.getId()
                    ? new Canonical(current, related, RelationshipType.RELATES_TO)
                    : new Canonical(related, current, RelationshipType.RELATES_TO);
        };
    }

    private List<Response> responses(Long projectId, Long taskId) {
        return linkRepository.findForTask(projectId, taskId).stream().map(link -> response(link, taskId)).toList();
    }

    private Response response(ProjectTaskLink link, Long currentTaskId) {
        boolean forward = link.getSourceTask().getId().equals(currentTaskId);
        Request related = forward ? link.getTargetTask() : link.getSourceTask();
        PerspectiveType perspective = switch (link.getRelationshipType()) {
            case BLOCKS -> forward ? PerspectiveType.BLOCKS : PerspectiveType.IS_BLOCKED_BY;
            case DUPLICATES -> forward ? PerspectiveType.DUPLICATES : PerspectiveType.IS_DUPLICATED_BY;
            case RELATES_TO -> PerspectiveType.RELATES_TO;
        };
        String label = switch (perspective) {
            case BLOCKS -> "Blocks";
            case IS_BLOCKED_BY -> "Is Blocked By";
            case DUPLICATES -> "Duplicates";
            case IS_DUPLICATED_BY -> "Is Duplicated By";
            case RELATES_TO -> "Relates To";
        };
        return new Response(link.getId(), perspective, label, related.getId(), related.getTitle(),
                related.isSubtask(), link.getCreatedAt());
    }

    private List<Candidate> candidates(Long projectId, Long taskId) {
        return requestRepository.findByProject_IdOrderByCreatedAtDescIdDesc(projectId).stream()
                .filter(task -> !task.getId().equals(taskId))
                .map(task -> new Candidate(task.getId(), task.getTitle(), task.isSubtask()))
                .toList();
    }

    private Request requireTask(Long projectId, Long taskId) {
        return requestRepository.findByIdAndProject_Id(taskId, projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found."));
    }

    private Project requireProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found."));
    }

    private User requireActiveAdmin(Long adminId) {
        return userRepository.findById(adminId)
                .filter(User::isActive)
                .filter(user -> user.getRole() == User.Role.ADMIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access is required."));
    }

    private record Canonical(Request source, Request target, RelationshipType type) {}
}
