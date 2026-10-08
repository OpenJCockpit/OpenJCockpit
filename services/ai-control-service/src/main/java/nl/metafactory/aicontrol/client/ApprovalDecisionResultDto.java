package nl.metafactory.aicontrol.client;

public record ApprovalDecisionResultDto(String runId, String status, int iteration, String message) {
}
