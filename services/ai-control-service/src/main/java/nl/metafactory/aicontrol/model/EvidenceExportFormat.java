package nl.metafactory.aicontrol.model;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Optional;

/** File format of an evidence export. */
public enum EvidenceExportFormat {
    JSON("json", "application/json", "json"),
    CSV("csv", "text/csv", "csv"),
    MARKDOWN("markdown", "text/markdown", "md"),
    PDF("pdf", "application/pdf", "pdf");

    private final String value;
    private final String mediaType;
    private final String extension;

    EvidenceExportFormat(String value, String mediaType, String extension) {
        this.value = value;
        this.mediaType = mediaType;
        this.extension = extension;
    }

    @JsonValue
    public String getValue() { return value; }

    public String mediaType() { return mediaType; }

    public String extension() { return extension; }

    public static Optional<EvidenceExportFormat> fromValue(String value) {
        return Arrays.stream(values()).filter(f -> f.value.equals(value)).findFirst();
    }
}
