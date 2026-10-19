package nl.metafactory.aicontrol.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public record SpecQueueOrderRequest(
        @NotEmpty @Size(max = 500) List<@NotNull UUID> itemIds
) {
    @JsonIgnore
    @AssertTrue(message = "itemIds must be unique")
    public boolean isItemIdsUnique() {
        return itemIds == null || new HashSet<>(itemIds).size() == itemIds.size();
    }
}
