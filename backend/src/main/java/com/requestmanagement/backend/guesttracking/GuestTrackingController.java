package com.requestmanagement.backend.guesttracking;
import com.requestmanagement.backend.guestcommunication.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.util.List;

@RestController @RequestMapping("/api/guest/tracking") @RequiredArgsConstructor
public class GuestTrackingController {
 private final GuestTrackingService service;
 @Value("${masar.guest-tracking.cookie-secure}") private boolean secure;
 @PostMapping("/request-code") public GuestTrackingDtos.ChallengeResponse request(@Valid @RequestBody GuestTrackingDtos.RequestCodeInput input){return service.requestCode(input);}
 @PostMapping("/verify") public ResponseEntity<Void> verify(@Valid @RequestBody GuestTrackingDtos.VerifyInput input){return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE,cookie(service.verify(input),Duration.ofMinutes(30)).toString()).build();}
 @PostMapping("/resend") public GuestTrackingDtos.ChallengeResponse resend(@Valid @RequestBody GuestTrackingDtos.ResendInput input){return service.resend(input);}
 @GetMapping("/request") public GuestTrackingDtos.TrackingResponse tracking(@CookieValue(name=GuestTrackingService.COOKIE,required=false) String token){return service.tracking(token);}
 @GetMapping("/communications") public List<GuestCommunicationResponse> history(@CookieValue(name=GuestTrackingService.COOKIE,required=false) String token){return service.history(token);}
 @PostMapping("/communications") public ResponseEntity<GuestCommunicationResponse> send(@CookieValue(name=GuestTrackingService.COOKIE,required=false) String token,@Valid @RequestBody SendGuestCommunicationRequest input){return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.SET_COOKIE,cookie(token,Duration.ofMinutes(30)).toString()).body(service.send(token,input));}
 @PostMapping("/logout") public ResponseEntity<Void> logout(@CookieValue(name=GuestTrackingService.COOKIE,required=false) String token){service.revoke(token);return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE,cookie("",Duration.ZERO).toString()).build();}
 private ResponseCookie cookie(String value,Duration age){return ResponseCookie.from(GuestTrackingService.COOKIE,value).httpOnly(true).secure(secure).sameSite("Strict").path("/api/guest/tracking").maxAge(age).build();}
}
