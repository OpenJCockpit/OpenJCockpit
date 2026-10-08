package nl.metafactory.agents.workflow;

/**
 * Thrown by {@link YamlDefinitionStore}'s per-file read when one specific, explicitly
 * addressed definition file exists but could not be read or parsed.
 *
 * <p>Extends {@link java.io.UncheckedIOException} so every existing caller keeps working
 * unchanged.
 *
 * <p>SECURITY: {@link #getMessage()} is deliberately browser-safe, containing only the
 * relative file name and a sanitised reason. It must never include the cause's raw message.
 */
public class DefinitionFileReadException extends java.io.UncheckedIOException {

    private final String relativeName;
    private final String sanitisedReason;

    DefinitionFileReadException(String relativeName, String sanitisedReason, java.io.IOException cause) {
        super("Failed to read definition file '" + relativeName + "': " + sanitisedReason, cause);
        this.relativeName = relativeName;
        this.sanitisedReason = sanitisedReason;
    }

    /** Two-segment path relative to the definitions root, e.g. workflows/wf-x.yaml. */
    public String relativeName() { return relativeName; }

    /** Sanitised reason, e.g. StreamReadException at line 7, column 3. */
    public String sanitisedReason() { return sanitisedReason; }
}
