package com.requestmanagement.backend.request;

import com.requestmanagement.backend.comment.CommentRepository;
import com.requestmanagement.backend.attachment.TaskAttachmentRepository;
import com.requestmanagement.backend.guestcommunication.GuestCommunicationRepository;
import com.requestmanagement.backend.guestform.RequestGuestFieldAnswerRepository;
import com.requestmanagement.backend.guesttracking.GuestTrackingChallengeRepository;
import com.requestmanagement.backend.guesttracking.GuestTrackingSessionRepository;
import com.requestmanagement.backend.statushistory.StatusHistoryRepository;
import com.requestmanagement.backend.tasklink.ProjectTaskLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RequestPermanentDeletionService {
    private final GuestTrackingSessionRepository sessionRepository;
    private final GuestTrackingChallengeRepository challengeRepository;
    private final GuestCommunicationRepository communicationRepository;
    private final RequestGuestFieldAnswerRepository answerRepository;
    private final CommentRepository commentRepository;
    private final StatusHistoryRepository historyRepository;
    private final RequestRepository requestRepository;
    private final ProjectTaskLinkRepository taskLinkRepository;
    private final TaskAttachmentRepository taskAttachmentRepository;

    @Transactional
    public void delete(Request request) {
        deleteAll(List.of(request));
    }

    @Transactional
    public void deleteAll(Collection<Request> requests) {
        if (requests.isEmpty()) return;
        List<Long> ids = requests.stream().map(Request::getId).toList();
        if (taskAttachmentRepository.existsByRequestIdIn(ids)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Task attachments must be cleaned from private storage before permanent deletion.");
        }
        if (ids.stream().anyMatch(requestRepository::existsByParentTask_Id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A parent Task with subtasks cannot be permanently deleted. Delete its subtasks first.");
        }
        taskLinkRepository.deleteByTaskIds(ids);
        taskLinkRepository.flush();
        sessionRepository.deleteByRequest_IdIn(ids);
        challengeRepository.deleteByRequest_IdIn(ids);
        communicationRepository.deleteByRequest_IdIn(ids);
        answerRepository.deleteByRequest_IdIn(ids);
        commentRepository.deleteByRequest_IdIn(ids);
        historyRepository.deleteByRequest_IdIn(ids);
        requestRepository.deleteAll(requests);
        requestRepository.flush();
    }

    @Transactional
    public void deleteParentWithSubtasks(Request parent, List<Request> subtasks) {
        if (parent.isSubtask() || subtasks.stream().anyMatch(task -> !task.isSubtask()
                || !task.getParentTask().getId().equals(parent.getId())
                || !task.getProject().getId().equals(parent.getProject().getId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task hierarchy changed. Refresh and try again.");
        }
        deleteAll(subtasks);
        delete(parent);
    }
}
