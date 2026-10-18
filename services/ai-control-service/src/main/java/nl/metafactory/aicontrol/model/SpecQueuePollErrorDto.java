package nl.metafactory.aicontrol.model;

import java.time.Instant;

public record SpecQueuePollErrorDto(String code, Instant at) {}
