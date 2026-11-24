package nl.metafactory.aicontrol.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum EvidenceExportScope {
    RUN("run"), WORKFLOW("workflow"), PROJECT("project");

    private final String value;

    EvidenceExportScope(String value) { this.value = value; }

    @JsonValue
    public String getValue() { return value; }
}
