package nl.metafactory.aicontrol.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum EvidenceExportJobStatus {
    QUEUED("queued"), RUNNING("running"), DONE("done"), FAILED("failed"), CANCELLED("cancelled");

    private final String value;

    EvidenceExportJobStatus(String value) { this.value = value; }

    @JsonValue
    public String getValue() { return value; }
}
