package nl.metafactory.aicontrol.model;

import jakarta.validation.constraints.NotNull;

public record SpecQueueSettingsRequest(@NotNull Boolean autoMergeAllowed) {}
