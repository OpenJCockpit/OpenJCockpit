package nl.metafactory.aicontrol.model;

import java.util.List;

public record GitConnectionCheckResult(
        GitStatus gitStatus,
        String message,
        List<String> warnings
) {}
