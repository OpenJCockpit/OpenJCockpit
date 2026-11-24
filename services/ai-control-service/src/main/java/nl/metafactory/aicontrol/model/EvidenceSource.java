package nl.metafactory.aicontrol.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum EvidenceSource {
    AGENT("agent"), APPROVAL("approval"), GIT("git");

    private final String value;

    EvidenceSource(String value) { this.value = value; }

    @JsonValue
    public String getValue() { return value; }
}
