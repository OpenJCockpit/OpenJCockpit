package nl.metafactory.aicontrol.model;

import java.util.List;

public record ModelRouteDecision(
        String route,
        String modelProfile,
        boolean cloudAllowed,
        String reason,
        List<String> requiredControls
) {}
