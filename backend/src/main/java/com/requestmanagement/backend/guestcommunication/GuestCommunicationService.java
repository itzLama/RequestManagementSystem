package com.requestmanagement.backend.guestcommunication;

import java.util.List;

import com.requestmanagement.backend.project.Project;
import com.requestmanagement.backend.project.ProjectAccessService;
import com.requestmanagement.backend.project.ProjectRepository;
import com.requestmanagement.backend.notification.GuestEmailNotificationEvent;
import com.requestmanagement.backend.request.Request;
import com.requestmanagement.backend.request.RequestRepository;
import com.requestmanagement.backend.user.User;
import com.requestmanagement.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class GuestCommunicationService {
    private final GuestCommunicationRepository communicationRepository;
    private final RequestRepository requestRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectAccessService projectAccessService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<GuestCommunicationResponse> adminGeneralHistory(Long requestId, Long adminId) {
        requireActiveAdmin(adminId);
        Request request = findGeneralForAdmin(requestId);
        requireGuest(request);
        return history(requestId);
    }

    @Transactional
    public GuestCommunicationResponse sendAdminGeneral(
            Long requestId, Long adminId, SendGuestCommunicationRequest input
    ) {
        User sender = requireActiveAdmin(adminId);
        Request request = findGeneralForAdmin(requestId);
        requireGuest(request);
        return save(request, sender, input);
    }

    @Transactional(readOnly = true)
    public List<GuestCommunicationResponse> assignedGeneralHistory(Long requestId, Long employeeId) {
        requireActiveEmployee(employeeId);
        Request request = findAssignedGeneral(requestId, employeeId);
        requireGuest(request);
        return history(requestId);
    }

    @Transactional
    public GuestCommunicationResponse sendAssignedGeneral(
            Long requestId, Long employeeId, SendGuestCommunicationRequest input
    ) {
        User sender = requireActiveEmployee(employeeId);
        Request request = findAssignedGeneral(requestId, employeeId);
        requireGuest(request);
        return save(request, sender, input);
    }

    @Transactional(readOnly = true)
    public List<GuestCommunicationResponse> adminProjectHistory(
            Long projectId, Long taskId, Long adminId
    ) {
        requireActiveAdmin(adminId);
        requireProject(projectId);
        Request task = findProjectTask(projectId, taskId);
        requireGuest(task);
        return history(taskId);
    }

    @Transactional
    public GuestCommunicationResponse sendAdminProject(
            Long projectId, Long taskId, Long adminId, SendGuestCommunicationRequest input
    ) {
        User sender = requireActiveAdmin(adminId);
        Project project = requireProject(projectId);
        projectAccessService.requireActive(project);
        Request task = findProjectTask(projectId, taskId);
        requireGuest(task);
        return save(task, sender, input);
    }

    @Transactional(readOnly = true)
    public List<GuestCommunicationResponse> employeeProjectHistory(
            Long projectId, Long taskId, Long employeeId
    ) {
        projectAccessService.requireAccessibleProject(projectId, employeeId);
        Request task = findProjectTask(projectId, taskId);
        requireGuest(task);
        return history(taskId);
    }

    @Transactional
    public GuestCommunicationResponse sendEmployeeProject(
            Long projectId, Long taskId, Long employeeId, SendGuestCommunicationRequest input
    ) {
        Project project = projectAccessService.requireMutableProject(projectId, employeeId);
        User sender = projectAccessService.requireActiveEmployee(employeeId);
        Request task = findProjectTask(projectId, taskId);
        requireGuest(task);
        return save(task, sender, input);
    }

    private GuestCommunicationResponse save(
            Request request, User sender, SendGuestCommunicationRequest input
    ) {
        String message = input.message().trim();
        if (message.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message is required.");
        }
        if (message.length() > 10000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Message must be at most 10000 characters.");
        }
        GuestCommunication communication = communicationRepository.save(
                GuestCommunication.create(request, sender, message));
        eventPublisher.publishEvent(GuestEmailNotificationEvent.messageSent(communication));
        return GuestCommunicationResponse.from(communication);
    }

    private List<GuestCommunicationResponse> history(Long requestId) {
        return communicationRepository.findByRequest_IdOrderBySentAtAscIdAsc(requestId).stream()
                .map(GuestCommunicationResponse::from)
                .toList();
    }

    private Request findGeneralForAdmin(Long requestId) {
        return requestRepository.findAdminDetailsByIdAndProjectIsNull(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found."));
    }

    private Request findAssignedGeneral(Long requestId, Long employeeId) {
        return requestRepository.findByIdAndAssignedTo_IdAndProjectIsNull(requestId, employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found."));
    }

    private Request findProjectTask(Long projectId, Long taskId) {
        return requestRepository.findByIdAndProject_Id(taskId, projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found."));
    }

    private Project requireProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found."));
    }

    private User requireActiveAdmin(Long userId) {
        return userRepository.findById(userId)
                .filter(User::isActive)
                .filter(user -> user.getRole() == User.Role.ADMIN)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Administrator access is required."));
    }

    private User requireActiveEmployee(Long userId) {
        return userRepository.findByIdAndRole(userId, User.Role.EMPLOYEE)
                .filter(User::isActive)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Active Employee access is required."));
    }

    private void requireGuest(Request request) {
        if (!request.isGuest()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Guest Communication is available only for Guest requests.");
        }
    }
}
