package nl.metafactory.aicontrol.client;

public record ApprovalDecisionRequestDto(ApprovalDecisionKind decision, int iteration, String comment) {
}
