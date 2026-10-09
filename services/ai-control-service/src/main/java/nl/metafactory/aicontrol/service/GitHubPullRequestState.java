package nl.metafactory.aicontrol.service;

public record GitHubPullRequestState(boolean open, boolean merged, boolean draft, Boolean mergeable,
                                     String mergeableState, String headSha, String baseRef,
                                     int checksPending, int checksFailing, int checksSucceeded) {}
