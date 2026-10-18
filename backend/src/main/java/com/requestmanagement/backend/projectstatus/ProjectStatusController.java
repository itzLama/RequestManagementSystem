package com.requestmanagement.backend.projectstatus;
import com.requestmanagement.backend.project.ProjectAccessService;
import com.requestmanagement.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor
public class ProjectStatusController {
 private final ProjectStatusService service; private final ProjectAccessService access;
 @GetMapping("/api/projects/{projectId}/statuses") @PreAuthorize("hasRole('ADMIN')") public List<ProjectStatusDtos.Response> adminList(@PathVariable Long projectId){return service.list(projectId);}
 @PostMapping("/api/projects/{projectId}/statuses") @PreAuthorize("hasRole('ADMIN')") public ResponseEntity<ProjectStatusDtos.Response> create(@PathVariable Long projectId,@Valid @RequestBody ProjectStatusDtos.NameInput input){return ResponseEntity.status(HttpStatus.CREATED).body(service.create(projectId,input));}
 @PutMapping("/api/projects/{projectId}/statuses/{statusId}") @PreAuthorize("hasRole('ADMIN')") public ProjectStatusDtos.Response rename(@PathVariable Long projectId,@PathVariable Long statusId,@Valid @RequestBody ProjectStatusDtos.NameInput input){return service.rename(projectId,statusId,input);}
 @DeleteMapping("/api/projects/{projectId}/statuses/{statusId}") @PreAuthorize("hasRole('ADMIN')") public ResponseEntity<Void> delete(@PathVariable Long projectId,@PathVariable Long statusId,@RequestBody(required=false) ProjectStatusDtos.DeleteInput input,@AuthenticationPrincipal AuthenticatedUser principal){service.delete(projectId,statusId,input==null?null:input.replacementStatusId(),principal.userId());return ResponseEntity.noContent().build();}
 @PutMapping("/api/projects/{projectId}/statuses/order") @PreAuthorize("hasRole('ADMIN')") public List<ProjectStatusDtos.Response> reorder(@PathVariable Long projectId,@Valid @RequestBody ProjectStatusDtos.ReorderInput input){return service.reorder(projectId,input.statusIds());}
 @GetMapping("/api/projects/mine/{projectId}/statuses") @PreAuthorize("hasRole('EMPLOYEE')") public List<ProjectStatusDtos.Response> employeeList(@PathVariable Long projectId,@AuthenticationPrincipal AuthenticatedUser principal){access.requireAccessibleProject(projectId,principal.userId());return service.list(projectId);}
}
