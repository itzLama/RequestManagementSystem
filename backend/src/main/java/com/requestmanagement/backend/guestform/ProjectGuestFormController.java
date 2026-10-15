package com.requestmanagement.backend.guestform;
import com.requestmanagement.backend.guestform.GuestFormDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/projects/{projectId}/guest-form") @PreAuthorize("hasRole('ADMIN')") @RequiredArgsConstructor
public class ProjectGuestFormController {
 private final ProjectGuestFormService service;
 @GetMapping public SettingsResponse get(@PathVariable Long projectId){return service.admin(projectId);}
 @PutMapping("/settings") public SettingsResponse settings(@PathVariable Long projectId,@Valid @RequestBody SettingsInput in){return service.updateSettings(projectId,in);}
 @PostMapping("/fields") public ResponseEntity<FieldResponse> create(@PathVariable Long projectId,@Valid @RequestBody FieldInput in){return ResponseEntity.status(HttpStatus.CREATED).body(service.create(projectId,in));}
 @PutMapping("/fields/{fieldId}") public FieldResponse update(@PathVariable Long projectId,@PathVariable Long fieldId,@Valid @RequestBody FieldInput in){return service.update(projectId,fieldId,in);}
 @DeleteMapping("/fields/{fieldId}") public ResponseEntity<Void> retire(@PathVariable Long projectId,@PathVariable Long fieldId){service.retire(projectId,fieldId);return ResponseEntity.noContent().build();}
 @PatchMapping("/fields/{fieldId}/order") public java.util.List<FieldResponse> reorder(@PathVariable Long projectId,@PathVariable Long fieldId,@Valid @RequestBody ReorderInput in){return service.reorder(projectId,fieldId,in);}
}
