package nl.metafactory.aicontrol.service;

import org.eclipse.jgit.transport.CredentialsProvider;

@FunctionalInterface
public interface JGitPort {
    void checkReachable(String url, CredentialsProvider credentials, int timeoutSeconds) throws Exception;
}