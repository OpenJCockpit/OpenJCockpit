package nl.metafactory.aicontrol.specqueue.runner;

/** The runner token secret is blank. The message never carries configuration values. */
public class RunnerIdentityUnconfiguredException extends RuntimeException {
    public RunnerIdentityUnconfiguredException() {
        super("Spec-queue runner identity is not configured");
    }
}
