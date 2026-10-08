package com.requestmanagement.backend.guest;

import com.requestmanagement.backend.project.Project;
import com.requestmanagement.backend.project.ProjectRepository;
import com.requestmanagement.backend.project.ProjectStatus;
import com.requestmanagement.backend.request.Request;
import com.requestmanagement.backend.request.RequestRepository;
import com.requestmanagement.backend.requesttype.RequestType;
import com.requestmanagement.backend.requesttype.RequestTypeRepository;
import com.requestmanagement.backend.requesttype.RequestTypeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class GuestRequestService {
    private final RequestRepository requestRepository;
    private final RequestTypeRepository requestTypeRepository;
    private final ProjectRepository projectRepository;

    @Transactional(readOnly = true)
    public List<RequestTypeResponse> requestTypes() {
        return requestTypeRepository.findByActiveTrueOrderByTypeNameAsc().stream().map(RequestTypeResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PublicProjectResponse> projects() {
        return projectRepository.findByStatusOrderByNameAscIdAsc(ProjectStatus.ACTIVE).stream().map(PublicProjectResponse::from).toList();
    }

    @Transactional
    public GuestRequestCreatedResponse createGeneral(CreateGuestGeneralRequest input) {
        RequestType type = requestTypeRepository.findById(input.typeId()).filter(RequestType::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request type must exist and be active."));
        Request request = Request.createGuestGeneralRequest(normalize(input.title(), "Title"), normalize(input.description(), "Description"),
                type, input.priority(), normalize(input.guestName(), "Guest name"), normalizeEmail(input.guestEmail()));
        return new GuestRequestCreatedResponse(requestRepository.save(request).getId());
    }

    @Transactional
    public GuestRequestCreatedResponse createProject(Long projectId, CreateGuestProjectRequest input) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found."));
        if (!project.isActive()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived projects cannot receive requests.");
        Request request = Request.createGuestProjectRequest(normalize(input.title(), "Title"), normalize(input.description(), "Description"),
                input.workType(), input.priority(), normalize(input.guestName(), "Guest name"), normalizeEmail(input.guestEmail()), project);
        return new GuestRequestCreatedResponse(requestRepository.save(request).getId());
    }

    private String normalize(String value, String field) {
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required.");
        return normalized;
    }

    private String normalizeEmail(String value) {
        return normalize(value, "Guest email").toLowerCase(Locale.ROOT);
    }
}
