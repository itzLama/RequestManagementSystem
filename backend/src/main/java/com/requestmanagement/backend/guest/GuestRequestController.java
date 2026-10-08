package com.requestmanagement.backend.guest;

import com.requestmanagement.backend.requesttype.RequestTypeResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/guest")
@RequiredArgsConstructor
public class GuestRequestController {
    private final GuestRequestService guestRequestService;

    @GetMapping("/request-types")
    public List<RequestTypeResponse> requestTypes() { return guestRequestService.requestTypes(); }

    @GetMapping("/projects")
    public List<PublicProjectResponse> projects() { return guestRequestService.projects(); }

    @PostMapping("/requests/general")
    public ResponseEntity<GuestRequestCreatedResponse> createGeneral(@Valid @RequestBody CreateGuestGeneralRequest input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(guestRequestService.createGeneral(input));
    }

    @PostMapping("/requests/projects/{projectId}")
    public ResponseEntity<GuestRequestCreatedResponse> createProject(
            @PathVariable @Positive(message = "Project ID must be positive.") Long projectId,
            @Valid @RequestBody CreateGuestProjectRequest input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(guestRequestService.createProject(projectId, input));
    }
}
