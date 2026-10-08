package nl.metafactory.aicontrol.model;

public record SpecFile(
        String id,
        String fileName,
        String owner,
        String lastChanged,
        String status,
        boolean selected,
        String content,
        String repositoryUrl
) {}
