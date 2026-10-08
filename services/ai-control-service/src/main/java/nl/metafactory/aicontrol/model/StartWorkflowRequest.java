package nl.metafactory.aicontrol.model;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record StartWorkflowRequest(
        @NotBlank String specFileRef,
        UUID projectId,
        Boolean dryRun
) {}
