package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.model.GitWorkspaceJobErrorCode;

public class GitWorkspaceException extends RuntimeException {

    private final GitWorkspaceJobErrorCode errorCode;

    public GitWorkspaceException(GitWorkspaceJobErrorCode errorCode, String details) {
        super(errorCode.name() + ": " + details);
        this.errorCode = errorCode;
    }

    public GitWorkspaceException(GitWorkspaceJobErrorCode errorCode, String details, Throwable cause) {
        super(errorCode.name() + ": " + details, cause);
        this.errorCode = errorCode;
    }

    public GitWorkspaceJobErrorCode getErrorCode() { return errorCode; }
}