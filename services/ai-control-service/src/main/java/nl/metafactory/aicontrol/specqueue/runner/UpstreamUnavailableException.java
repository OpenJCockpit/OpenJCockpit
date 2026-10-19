package nl.metafactory.aicontrol.specqueue.runner;

public class UpstreamUnavailableException extends RuntimeException {

    private final EmbabelRunnerClient.FailureCode code;

    public UpstreamUnavailableException(EmbabelRunnerClient.FailureCode code) {
        super("Embabel agent service unavailable: " + code);
        this.code = code;
    }

    public EmbabelRunnerClient.FailureCode code() {
        return code;
    }
}
