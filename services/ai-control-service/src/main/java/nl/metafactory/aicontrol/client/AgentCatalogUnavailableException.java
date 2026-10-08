package nl.metafactory.aicontrol.client;

public class AgentCatalogUnavailableException extends RuntimeException {

    public enum Code {
        UPSTREAM_UNREACHABLE("The agent catalogue could not be reached."),
        UPSTREAM_TIMEOUT("The agent catalogue did not respond in time."),
        UPSTREAM_UNAUTHORIZED("The agent catalogue rejected the request as unauthorized."),
        UPSTREAM_FORBIDDEN("The agent catalogue rejected the request as forbidden."),
        UPSTREAM_ERROR("The agent catalogue returned an unexpected error."),
        UPSTREAM_RESPONSE_INVALID("The agent catalogue returned a response that could not be understood.");

        private final String message;

        Code(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    private final Code code;

    public AgentCatalogUnavailableException(Code code) {
        super(code.message());
        this.code = code;
    }

    public AgentCatalogUnavailableException(Code code, Throwable cause) {
        super(code.message(), cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
