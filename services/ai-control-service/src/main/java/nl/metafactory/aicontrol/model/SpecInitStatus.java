package nl.metafactory.aicontrol.model;

public record SpecInitStatus(
        boolean pending,
        String branch,
        String branchUrl,
        String pullRequestUrl,
        boolean templateExists
) {
    public static SpecInitStatus none(boolean templateExists) {
        return new SpecInitStatus(false, null, null, null, templateExists);
    }
}
