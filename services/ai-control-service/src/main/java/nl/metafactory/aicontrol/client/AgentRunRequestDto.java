package nl.metafactory.aicontrol.client;

import java.util.List;

public record AgentRunRequestDto(String customerId, String specFile, List<String> agentIds, String requestedBy, String repositoryUrl) {}
