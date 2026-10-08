package nl.metafactory.aicontrol.model;

public record QualityControl(
        String name,
        String status,
        String statusText
) {}
