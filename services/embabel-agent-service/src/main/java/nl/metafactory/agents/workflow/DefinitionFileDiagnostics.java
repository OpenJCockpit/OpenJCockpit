package nl.metafactory.agents.workflow;

import tools.jackson.core.JacksonException;
import tools.jackson.core.TokenStreamLocation;
import java.nio.file.Path;

/**
 * Pure helpers for building browser-safe diagnostics about a definition file read failure.
 *
 * <p>SECURITY: the produced strings are deliberately limited to a relative file name and a
 * sanitised reason (exception kind plus optional line/column). They must never include the
 * cause's raw message, which could leak absolute paths or other sensitive details.
 */
final class DefinitionFileDiagnostics {

    private DefinitionFileDiagnostics() {}

    static String relativeName(Path file) {
        Path name = file.getFileName();
        Path parent = file.getParent();
        if (name == null) return String.valueOf(file);
        if (parent == null || parent.getFileName() == null) return name.toString();
        return parent.getFileName() + "/" + name;
    }

    static String sanitisedReason(Throwable e) {
        String kind = e.getClass().getSimpleName();
        if (!(e instanceof JacksonException jackson)) return kind;
        TokenStreamLocation location = jackson.getLocation();
        if (location == null || location == TokenStreamLocation.NA) return kind;
        int line = location.getLineNr();
        int column = location.getColumnNr();
        if (line < 0 && column < 0) return kind;
        return kind + " at line " + line + ", column " + column;
    }
}
