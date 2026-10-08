package nl.metafactory.aicontrol.model;

public record PullRequestSummary(
        String id,
        String title,
        String branch,
        String createdAt,
        String ownerLabel,
        String ownerName,
        String status,
        String statusKind
) {}
