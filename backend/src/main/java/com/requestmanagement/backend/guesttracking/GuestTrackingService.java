package com.requestmanagement.backend.guesttracking;

import com.requestmanagement.backend.guestform.*;
import com.requestmanagement.backend.guestcommunication.*;
import com.requestmanagement.backend.notification.GuestEmailNotificationEvent;
import com.requestmanagement.backend.project.ProjectStatus;
import com.requestmanagement.backend.request.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class GuestTrackingService {
 public static final String COOKIE="MASAR_GUEST_TRACKING";
 private static final String GENERIC="If the provided details match a request, a verification code will be sent to the registered email.";
 private final GuestTrackingChallengeRepository challenges; private final GuestTrackingSessionRepository sessions;
 private final RequestRepository requests; private final RequestGuestFieldAnswerRepository answers;
 private final GuestCommunicationRepository communications; private final GuestTrackingCrypto crypto; private final ApplicationEventPublisher events;
 @Transactional public GuestTrackingDtos.ChallengeResponse requestCode(GuestTrackingDtos.RequestCodeInput input){
  LocalDateTime now=LocalDateTime.now();String number=input.trackingNumber().trim().replace("#","");String email=input.email().trim().toLowerCase(Locale.ROOT);
  String lookup=crypto.digest("lookup",number+"|"+email);if(challenges.countByLookupDigestAndCreatedAtAfter(lookup,now.minusHours(1))>=5)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Please wait before requesting another verification code.");
  Request request=null;try{request=requests.findByIdAndGuestEmailIgnoreCaseAndCreatedByIsNull(Long.valueOf(number),email).orElse(null);}catch(NumberFormatException ignored){}
  if(request!=null)challenges.findByRequest_IdAndConsumedAtIsNullAndSupersededAtIsNull(request.getId()).forEach(c->c.supersede(now));
  String code=crypto.code();var challenge=challenges.save(GuestTrackingChallenge.create(request,lookup,crypto.digest("otp",code),now));
  if(request!=null)events.publishEvent(GuestEmailNotificationEvent.verification(request,code));
  return new GuestTrackingDtos.ChallengeResponse(challenge.getId(),GENERIC);
 }
 @Transactional(noRollbackFor=ResponseStatusException.class) public String verify(GuestTrackingDtos.VerifyInput input){LocalDateTime now=LocalDateTime.now();var c=challenges.findLocked(input.challengeId()).orElseThrow(()->invalid());if(!c.available(now))throw invalid();if(c.getRequest()==null||!crypto.matches(c.getOtpDigest(),crypto.digest("otp",input.code()))){c.fail();throw invalid();}c.consume(now);String token=crypto.token();sessions.save(GuestTrackingSession.create(c.getRequest(),crypto.digest("session",token),now));return token;}
 @Transactional public GuestTrackingDtos.ChallengeResponse resend(GuestTrackingDtos.ResendInput input){LocalDateTime now=LocalDateTime.now();var c=challenges.findLocked(input.challengeId()).orElseThrow(()->invalid());if(!c.available(now))throw invalid();if(c.getLastSentAt()!=null&&c.getLastSentAt().plusSeconds(60).isAfter(now))throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"A new code can be requested after 60 seconds.");if(c.getSentCount()>=5)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Verification code limit reached. Please start again later.");String code=crypto.code();c.resend(crypto.digest("otp",code),now);if(c.getRequest()!=null)events.publishEvent(GuestEmailNotificationEvent.verification(c.getRequest(),code));return new GuestTrackingDtos.ChallengeResponse(c.getId(),GENERIC);}
 @Transactional public GuestTrackingSession require(String token){if(token==null||token.isBlank())throw unauthorized();LocalDateTime now=LocalDateTime.now();var s=sessions.findLockedByTokenDigest(crypto.digest("session",token)).orElseThrow(this::unauthorized);if(!s.active(now)){s.revoke(now);throw unauthorized();}return s;}
 @Transactional public GuestTrackingDtos.TrackingResponse tracking(String token){Request r=require(token).getRequest();boolean projectRequest=r.isProjectTask();return new GuestTrackingDtos.TrackingResponse(String.valueOf(r.getId()),r.getTitle(),r.statusDisplayName(),projectRequest?null:r.getType().getTypeName(),projectRequest?r.getWorkType().getName():null,r.getPriority(),r.getCreatedAt(),r.getDescription(),answers.findByRequest_IdOrderByDisplayOrderAscIdAsc(r.getId()).stream().map(GuestFormDtos.AnswerResponse::from).toList(),projectRequest,projectRequest?r.getProject().getStatus()==ProjectStatus.ACTIVE:true);}
 @Transactional public List<GuestCommunicationResponse> history(String token){Request r=require(token).getRequest();return communications.findByRequest_IdOrderBySentAtAscIdAsc(r.getId()).stream().map(GuestCommunicationResponse::from).toList();}
 @Transactional public GuestCommunicationResponse send(String token,SendGuestCommunicationRequest input){GuestTrackingSession session=require(token);Request r=session.getRequest();if(r.isProjectTask()&&!r.getProject().isActive())throw new ResponseStatusException(HttpStatus.CONFLICT,"Archived projects are read-only.");String message=input.message().trim();if(message.isEmpty()||message.length()>10000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Message must contain between 1 and 10000 characters.");GuestCommunication saved=communications.save(GuestCommunication.createGuest(r,message));session.touch(LocalDateTime.now());return GuestCommunicationResponse.from(saved);}
 @Transactional public void revoke(String token){if(token==null)return;sessions.findLockedByTokenDigest(crypto.digest("session",token)).ifPresent(s->s.revoke(LocalDateTime.now()));}
 private ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.UNAUTHORIZED,"The verification code is invalid or expired.");}
 private ResponseStatusException unauthorized(){return new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Guest verification is required.");}
}
