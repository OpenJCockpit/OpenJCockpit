package nl.metafactory.agents.orchestration;

import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Pure, stateless sanitiser that converts a {@link Throwable} into a bounded, browser-safe
 * summary string suitable for display to an end user.
 *
 * <p>SECURITY: this class never calls {@link Throwable#getMessage()} or
 * {@link Throwable#toString()} on the input throwable or on any throwable in its cause chain, at
 * any depth. This is a deliberate abstention, not a filter — the raw message of an exception may
 * contain absolute file paths, repository URLs with embedded credentials, tokens, or other
 * sensitive material, and no attempt is made to read and then sanitise it. Only the exception's
 * simple class name, its cause chain, and a small allow-listed set of dedicated, already-sanitised
 * accessors on specific known exception types are ever consulted.
 */
public final class RunFailureDiagnostics {

    /** The maximum length of a summary produced by {@link #summarise(Throwable)}. */
    public static final int MAX_LENGTH = 512;

    private static final int MAX_CHAIN_DEPTH = 5;

    private RunFailureDiagnostics() {
    }

    /**
     * Builds a bounded, browser-safe summary of {@code t} and its cause chain.
     *
     * <p>The summary consists of the simple class name of each throwable in the cause chain
     * (up to {@link #MAX_CHAIN_DEPTH} links, cycle-safe), joined by {@code " <- "}, optionally
     * followed by a truncation marker if the chain was longer than the depth budget, optionally
     * followed by one allow-listed, already-sanitised reason describing the outermost throwable,
     * normalised to remove line breaks and tabs, and finally bounded to {@link #MAX_LENGTH}
     * characters with a trailing {@code "..."} marker if truncated.
     *
     * @param t the throwable to summarise, or {@code null}
     * @return the bounded summary, or {@code null} if {@code t} is {@code null}
     */
    public static String summarise(Throwable t) {
        if (t == null) {
            return null;
        }

        Map<Throwable, Boolean> visited = new IdentityHashMap<>();
        StringBuilder chain = new StringBuilder();
        Throwable current = t;
        int depth = 0;
        boolean truncated = false;

        while (current != null && depth < MAX_CHAIN_DEPTH) {
            if (visited.containsKey(current)) {
                break;
            }
            visited.put(current, Boolean.TRUE);

            if (depth > 0) {
                chain.append(" <- ");
            }
            chain.append(simpleNameOf(current));

            Throwable next = current.getCause();
            depth++;
            if (next != null && depth >= MAX_CHAIN_DEPTH && !visited.containsKey(next)) {
                truncated = true;
            }
            current = next;
        }

        if (truncated) {
            chain.append(" <- ...");
        }

        chain.append(allowListedReasonFor(t));

        String normalised = chain.toString().replaceAll("[\\r\\n\\t]", " ");
        if (normalised.length() > MAX_LENGTH) {
            return normalised.substring(0, MAX_LENGTH - 3) + "...";
        }
        return normalised;
    }

    private static String simpleNameOf(Throwable link) {
        String simpleName = link.getClass().getSimpleName();
        if (simpleName == null || simpleName.trim().isEmpty()) {
            return "UnknownThrowable";
        }
        return simpleName;
    }

    private static String allowListedReasonFor(Throwable t) {
        if (t instanceof nl.metafactory.agents.workflow.DefinitionFileReadException definitionFileReadException) {
            return " (" + definitionFileReadException.relativeName() + ": "
                    + definitionFileReadException.sanitisedReason() + ")";
        }
        if (t instanceof ResponseStatusException responseStatusException) {
            return " (HTTP " + responseStatusException.getStatusCode().value() + ")";
        }
        if (t instanceof RestClientResponseException restClientResponseException) {
            return " (HTTP " + restClientResponseException.getStatusCode().value() + ")";
        }
        return "";
    }
}
