package nl.metafactory.aicontrol.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SpecQueueEnqueueRequest(
        @NotBlank @Size(max = 500) String specFile,
        @NotBlank @Size(max = 200) String workflowId,
        boolean autoMerge
) {}
