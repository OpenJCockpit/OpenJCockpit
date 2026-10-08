package nl.metafactory.aicontrol.model;

import java.util.UUID;

public record SpecInitResult(
        UUID projectId,
        String branch,
        String baseBranch,
        String commitHash,
        String fileName,
        String pullRequestUrl,
        String message
) {}
