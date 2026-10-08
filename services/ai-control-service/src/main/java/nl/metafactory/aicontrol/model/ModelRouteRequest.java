package nl.metafactory.aicontrol.model;

import jakarta.validation.constraints.NotBlank;

public record ModelRouteRequest(
        @NotBlank String customerId,
        @NotBlank String specFile,
        DataClassification dataClassification,
        @NotBlank String taskType
) {}
