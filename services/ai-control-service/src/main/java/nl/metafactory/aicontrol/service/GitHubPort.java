package nl.metafactory.aicontrol.service;

@FunctionalInterface
public interface GitHubPort {
    void checkRepository(String owner, String repo, String apiUrl, String token) throws Exception;
}