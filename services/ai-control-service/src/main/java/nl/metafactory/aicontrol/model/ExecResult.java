package nl.metafactory.aicontrol.model;

public record ExecResult(int exitCode, String stdout, String stderr) {
    public boolean success() { return exitCode == 0; }
}
