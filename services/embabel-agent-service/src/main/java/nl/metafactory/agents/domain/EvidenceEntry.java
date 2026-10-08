package nl.metafactory.agents.domain;

import java.time.Instant;

public record EvidenceEntry(Instant timestamp, String agentId, String action, String outcome) {}
