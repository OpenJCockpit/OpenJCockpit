package nl.metafactory.agentruntime.definition.runtime;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** Trace id handling without a tracing library: reuse a valid W3C {@code traceparent}, else generate. */
public final class TraceIds {

    private static final Pattern TRACEPARENT = Pattern.compile("00-([0-9a-f]{32})-[0-9a-f]{16}-[0-9a-f]{2}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private TraceIds() {
    }

    public static String fromTraceparentOrNew(String traceparent) {
        if (traceparent != null) {
            var m = TRACEPARENT.matcher(traceparent.trim());
            if (m.matches() && !m.group(1).equals("0".repeat(32))) {
                return m.group(1);
            }
        }
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
