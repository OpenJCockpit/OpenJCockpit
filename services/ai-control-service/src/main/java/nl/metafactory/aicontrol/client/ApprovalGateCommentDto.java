package nl.metafactory.aicontrol.client;

import java.time.Instant;

public record ApprovalGateCommentDto(int iteration, String author, String comment, Instant submittedAt) {
}
