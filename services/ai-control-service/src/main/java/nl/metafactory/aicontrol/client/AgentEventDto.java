package nl.metafactory.aicontrol.client;

import java.time.Instant;

public record AgentEventDto(Instant timestamp, String agentId, String title, String status, String evidenceRef) {}
