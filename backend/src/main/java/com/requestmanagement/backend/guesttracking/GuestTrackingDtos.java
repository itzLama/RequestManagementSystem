package com.requestmanagement.backend.guesttracking;
import com.requestmanagement.backend.guestform.GuestFormDtos.AnswerResponse;
import com.requestmanagement.backend.request.RequestPriority;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.*;
public final class GuestTrackingDtos { private GuestTrackingDtos(){}
 public record RequestCodeInput(@NotBlank @Size(max=30) String trackingNumber,@NotBlank @Email @Size(max=255) String email){}
 public record ChallengeResponse(UUID challengeId,String message){}
 public record VerifyInput(@NotNull UUID challengeId,@NotBlank @Pattern(regexp="\\d{6}") String code){@Override public String toString(){return "VerifyInput[challengeId="+challengeId+", code=<redacted>]";}}
 public record ResendInput(@NotNull UUID challengeId){}
 public record TrackingResponse(String trackingNumber,String title,String statusName,String requestTypeName,String workTypeName,RequestPriority priority,LocalDateTime submittedAt,String description,List<AnswerResponse> answers,boolean projectRequest,boolean communicationWritable){}
}
