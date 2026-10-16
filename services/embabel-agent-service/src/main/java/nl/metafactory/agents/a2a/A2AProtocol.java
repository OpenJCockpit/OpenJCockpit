package nl.metafactory.agents.a2a;

/**
 * The A2A protocol subset this server implements: version 0.3 (card {@code protocolVersion}
 * "0.3.0"), JSON-RPC transport, synchronous {@code message/send} returning a {@code Message}.
 * Method names and error codes are taken from {@code io.github.a2asdk:a2a-java-sdk-spec:0.3.2.Final}
 * (already on the classpath via Embabel), not re-invented. Streaming, tasks and push
 * notifications are not implemented and are rejected explicitly.
 */
public final class A2AProtocol {

    public static final String PROTOCOL_VERSION = "0.3.0";
    public static final String TRANSPORT = "JSONRPC";

    public static final String MESSAGE_SEND = "message/send";
    public static final String MESSAGE_STREAM = "message/stream";
    public static final String TASKS_GET = "tasks/get";
    public static final String TASKS_CANCEL = "tasks/cancel";

    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;
    public static final int TASK_NOT_FOUND = -32001;
    public static final int UNSUPPORTED_OPERATION = -32004;
    public static final int CONTENT_TYPE_NOT_SUPPORTED = -32005;

    /** Max accepted text of one inbound message. */
    public static final int MAX_MESSAGE_CHARS = 32 * 1024;

    private A2AProtocol() {
    }
}
