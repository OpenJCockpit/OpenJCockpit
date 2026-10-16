package nl.metafactory.agents.definition.markdown;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Splits a Markdown document into its leading {@code ---}-delimited YAML frontmatter and its body.
 * Pure string handling; YAML interpretation is left to the caller.
 */
public final class MarkdownFrontmatter {

    /** Upper bound for any definition file; larger inputs are rejected before parsing. */
    public static final int MAX_DOCUMENT_BYTES = 256 * 1024;

    public record Split(String yaml, String body) {
    }

    private MarkdownFrontmatter() {
    }

    public static Split split(String raw) {
        if (raw == null) {
            throw new InvalidDefinitionException("document is empty");
        }
        if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_DOCUMENT_BYTES) {
            throw new InvalidDefinitionException("document exceeds " + MAX_DOCUMENT_BYTES + " bytes");
        }
        String text = raw.startsWith("﻿") ? raw.substring(1) : raw;
        text = text.replace("\r\n", "\n");
        if (!text.startsWith("---\n")) {
            throw new InvalidDefinitionException("missing YAML frontmatter (document must start with ---)");
        }
        int closing = text.indexOf("\n---", 3);
        if (closing < 0) {
            throw new InvalidDefinitionException("frontmatter has no closing ---");
        }
        int afterMarker = closing + 4;
        if (afterMarker < text.length() && text.charAt(afterMarker) != '\n') {
            throw new InvalidDefinitionException("closing --- must be on its own line");
        }
        String yaml = text.substring(4, closing + 1);
        String body = afterMarker >= text.length() ? "" : text.substring(afterMarker + 1);
        return new Split(yaml, body.strip());
    }

    public static String sha256(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
