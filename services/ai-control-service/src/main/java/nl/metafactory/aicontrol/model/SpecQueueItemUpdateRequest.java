package nl.metafactory.aicontrol.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

public record SpecQueueItemUpdateRequest(
        @Size(min = 1, max = 200) String workflowId,
        Boolean autoMerge
) {
    @JsonIgnore
    @AssertTrue(message = "at least one of workflowId or autoMerge is required")
    public boolean isAtLeastOnePropertyPresent() {
        return workflowId != null || autoMerge != null;
    }
}
