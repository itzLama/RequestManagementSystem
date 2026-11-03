package com.requestmanagement.backend.request;

import com.requestmanagement.backend.comment.CommentRepository;
import com.requestmanagement.backend.guestcommunication.GuestCommunicationRepository;
import com.requestmanagement.backend.guestform.RequestGuestFieldAnswerRepository;
import com.requestmanagement.backend.guesttracking.GuestTrackingChallengeRepository;
import com.requestmanagement.backend.guesttracking.GuestTrackingSessionRepository;
import com.requestmanagement.backend.statushistory.StatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public void delete(Request request) {
        deleteAll(List.of(request));
    }

    @Transactional
    public void deleteAll(Collection<Request> requests) {
        if (requests.isEmpty()) return;
        List<Long> ids = requests.stream().map(Request::getId).toList();
        sessionRepository.deleteByRequest_IdIn(ids);
        challengeRepository.deleteByRequest_IdIn(ids);
        communicationRepository.deleteByRequest_IdIn(ids);
        answerRepository.deleteByRequest_IdIn(ids);
        commentRepository.deleteByRequest_IdIn(ids);
        historyRepository.deleteByRequest_IdIn(ids);
        requestRepository.deleteAll(requests);
        requestRepository.flush();
    }
}
