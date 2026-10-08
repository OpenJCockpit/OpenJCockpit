package nl.metafactory.aicontrol.model;

public record EvidenceEvent(
        String time,
        String title,
        String actor,
        String status
) {}
