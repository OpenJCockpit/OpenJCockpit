package nl.metafactory.aicontrol.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum EvidenceStatus {
    OK("ok"), RUNNING("running"), WAITING("waiting"), FAILED("failed");

    private final String value;

    EvidenceStatus(String value) { this.value = value; }

    @JsonValue
    public String getValue() { return value; }
}
