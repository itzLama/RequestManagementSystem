package com.requestmanagement.backend.guestform;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class GuestFormDtos {
    private GuestFormDtos() { }
    public record SettingsInput(boolean workTypeVisible, @Positive Long defaultWorkTypeId,
                                boolean priorityVisible, boolean priorityRequired,
                                boolean descriptionVisible, boolean descriptionRequired) { }
    public record FieldInput(@NotBlank @Size(max=100) String label, @NotNull GuestFieldType type,
                             boolean required, @Size(max=30) List<@NotBlank @Size(max=100) String> options,
                             @Size(max=30) List<@Positive Long> optionIds) { }
    public record ReorderInput(@NotNull Direction direction) { public enum Direction { UP, DOWN } }
    public record AnswerInput(@NotNull @Positive Long fieldId, @Size(max=5000) String value) { }
    public record OptionResponse(Long id, String label) { static OptionResponse from(ProjectGuestFieldOption o) { return new OptionResponse(o.getId(), o.getLabel()); } }
    public record FieldResponse(Long id, String label, GuestFieldType type, boolean required, int displayOrder,
                                boolean active, List<OptionResponse> options) { }
    public record SettingsResponse(boolean workTypeVisible, Long defaultWorkTypeId, boolean priorityVisible,
                                   boolean priorityRequired, boolean descriptionVisible, boolean descriptionRequired,
                                   List<FieldResponse> fields) { }
    public record PublicSettingsResponse(boolean workTypeVisible, boolean priorityVisible, boolean priorityRequired,
                                         boolean descriptionVisible, boolean descriptionRequired,
                                         List<FieldResponse> fields) { }
    public record AnswerResponse(String label, GuestFieldType type, int displayOrder, String value, String optionLabel) {
        public static AnswerResponse from(RequestGuestFieldAnswer a) { return new AnswerResponse(a.getFieldLabel(), a.getFieldType(), a.getDisplayOrder(), a.getValue(), a.getOptionLabel()); }
    }
}
