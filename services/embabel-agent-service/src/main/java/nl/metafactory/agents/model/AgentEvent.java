package nl.metafactory.agents.model;

import java.time.Instant;

public record AgentEvent(
        Instant timestamp,
        String agentId,
        String title,
        String status,
        String evidenceRef
) {}
