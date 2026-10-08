package nl.metafactory.agents.approval.model;

import java.time.Instant;

/** One feedback comment given at a specific gate iteration (BR-25, N4). */
public record GateComment(int iteration, String author, String comment, Instant submittedAt) {
}
