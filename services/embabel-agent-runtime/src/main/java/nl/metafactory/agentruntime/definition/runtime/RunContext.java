package nl.metafactory.agentruntime.definition.runtime;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable correlation data that travels with one run: who, which agent snapshot, which trace, and
 * (inside a workflow) which workflow run — and, for runs on a project, git workspace — it belongs to.
 * Contains identifiers only — safe to log via {@link #logFields()}.
 */
public record RunContext(String runId, String traceId, String tenantId, String agentId, String version,
                         String contentHash, String principal,
                         // the workflow run this agent run belongs to; null outside a workflow
                         nl.metafactory.agentruntime.workflowrun.WorkflowRunScope workflow) {

    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._:@-]{1,128}");

    public RunContext {
        for (var entry : new String[][]{{"runId", runId}, {"traceId", traceId}, {"tenantId", tenantId},
                {"agentId", agentId}, {"version", version}, {"contentHash", contentHash}}) {
            if (entry[1] == null || !SAFE.matcher(entry[1]).matches()) {
                throw new IllegalArgumentException(entry[0] + " is missing or contains unsafe characters");
            }
        }
        Objects.requireNonNull(principal, "principal");
    }

    public RunContext(String runId, String traceId, String tenantId, String agentId, String version,
                      String contentHash, String principal) {
        this(runId, traceId, tenantId, agentId, version, contentHash, principal, null);
    }

    /** The git workspace of the workflow run (runs on a project), or null. */
    public nl.metafactory.agentruntime.gitworkspace.GitWorkspaceBinding gitWorkspace() {
        return workflow == null ? null : workflow.gitWorkspace();
    }

    public String logFields() {
        return "runId=" + runId + " traceId=" + traceId + " tenant=" + tenantId + " agentId=" + agentId
                + " version=" + version + " hash=" + contentHash.substring(0, Math.min(12, contentHash.length()));
    }
}
