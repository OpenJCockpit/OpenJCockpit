package nl.metafactory.aicontrol.model;

import jakarta.validation.constraints.NotBlank;

public record ProjectRequest(
        @NotBlank String name,
        String customerId,
        String gitUrl,
        String description,
        String defaultBranch,
        String environment,
        String owner,
        short newProject
) {}