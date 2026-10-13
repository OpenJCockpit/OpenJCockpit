package nl.metafactory.agents.spec;

public final class GitUrls {

    private GitUrls() {
    }

    /** GitHub compare URL, used as a manual fallback when no pull request exists; null for non-http URLs. */
    public static String compareUrl(String repositoryUrl, String branch) {
        if (repositoryUrl == null || !repositoryUrl.startsWith("http")) {
            return null;
        }
        String base = repositoryUrl.endsWith(".git")
                ? repositoryUrl.substring(0, repositoryUrl.length() - 4) : repositoryUrl;
        return base + "/compare/" + branch + "?expand=1";
    }
}
