package nl.metafactory.aicontrol.model;

/** Full output of one agent as included in an export; {@code truncated} when cut at the size cap. */
public record EvidenceExportOutput(String agentKey, String output, boolean truncated) {}
