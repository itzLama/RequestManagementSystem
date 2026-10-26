package com.requestmanagement.backend.projectstatus;
import jakarta.validation.constraints.*;
import java.util.List;
public final class ProjectStatusDtos {private ProjectStatusDtos(){}
 public record Response(Long id,String name,int displayOrder,boolean active,boolean protectedNew,String semanticKey){static Response from(ProjectStatusColumn s){return new Response(s.getId(),s.getName(),s.getDisplayOrder(),s.isActive(),s.isProtectedNew(),s.getSemanticKey()==null?null:s.getSemanticKey().name());}}
 public record NameInput(
         @NotBlank(message = "Status name is required.")
         @Size(max=50, message = "Status name must not exceed 50 characters.") String name){}
 public record CreateInput(
         @NotBlank(message = "Status name is required.")
         @Size(max=50, message = "Status name must not exceed 50 characters.") String name,
         @Min(value=1,message="Status position must keep New first.") Integer position){}
 public record ReorderInput(@NotEmpty List<@Positive Long> statusIds){}
 public enum DeleteMode { MOVE_TASKS, DELETE_TASKS }
 public record DeleteInput(@NotNull DeleteMode mode, @Positive Long replacementStatusId){}
 public record StatusInput(@Positive Long statusId,com.requestmanagement.backend.request.RequestStatus status,@Size(max=10000) String changeNote){}
}
