package nl.metafactory.agentruntime.definition.runtime;

/** Per-call tool authorisation (e.g. OPA). Must fail closed when its backend is unavailable. */
@FunctionalInterface
public interface ToolCallAuthorizer {

    record Decision(boolean allowed, String reason) {

        public static Decision allow() {
            return new Decision(true, null);
        }

        public static Decision deny(String reason) {
            return new Decision(false, reason);
        }
    }

    Decision authorize(RunContext run, String toolId);
}
