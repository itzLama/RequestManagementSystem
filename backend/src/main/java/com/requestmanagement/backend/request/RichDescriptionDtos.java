package com.requestmanagement.backend.request;

import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

public final class RichDescriptionDtos {
    private RichDescriptionDtos() { }

    public record UpdateInput(@NotNull JsonNode document) { }

    public record Response(String description, JsonNode descriptionDocument) {
        static Response from(Request task) {
            return new Response(task.getDescription(), task.getDescriptionDocument());
        }
    }
}
