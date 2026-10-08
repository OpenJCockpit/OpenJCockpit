package nl.metafactory.aicontrol.model;

import java.time.Instant;

public record SpecQueueSettingsDto(boolean autoMergeAllowed, Instant updatedAt) {}
