package nl.metafactory.agents.gitworkspace;

import nl.metafactory.agents.orchestration.RunCheckpoints;
import nl.metafactory.agents.workflowrun.WorkflowRunScope;
import nl.metafactory.common.mcp.GitMcpGateway;
import nl.metafactory.common.mcp.LiteLlmMcpToolClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Checkpoint commits of runs bound to a git workspace: after each agent, its full output is written to
 * {@code agent-output/<run-id>/<NN>-<agent>.md}, and everything the agents changed so far is committed
 * and pushed to the run's branch (git MCP server through LiteLLM; the server resolves the credentials).
 * Agents themselves never commit or push.
 */
@Component
public class GitWorkspaceCheckpoints implements RunCheckpoints {

    private static final Logger log = LoggerFactory.getLogger(GitWorkspaceCheckpoints.class);
    static final String AUTHOR = "Agentic Workflow";
    static final String AUTHOR_EMAIL = "agentic@metafactory.nl";

    private final GitMcpGateway gitMcp;
    private final GitWorkspaceProperties properties;

    public GitWorkspaceCheckpoints(GitMcpGateway agentGitMcpGateway, GitWorkspaceProperties properties) {
        this.gitMcp = agentGitMcpGateway;
        this.properties = properties;
    }

    @Override
    public Optional<String> afterAgent(WorkflowRunScope scope, String agentKey, int position, int total, String output) {
        GitWorkspaceBinding ws = scope == null ? null : scope.gitWorkspace();
        if (ws == null || !properties.isCheckpoints()) {
            return Optional.empty();
        }
        String runId = scope.workflowRunId();
        String agent = fileSafe(agentKey);
        try {
            String file = "agent-output/" + runId + "/" + String.format("%02d-%s.md", position + 1, agent);
            String content = "<!-- agent: " + agentKey + " | workflow: " + scope.workflowId() + " | run: " + runId
                    + " -->\n\n" + (output == null ? "" : output) + "\n";
            var written = gitMcp.writeFile(ws.repositoryUrl(), file, content, ws.workspaceKey());
            if (!written.success()) {
                return failed(ws, runId, agent, "agent output not written: " + written.message());
            }
            String message = "checkpoint " + (position + 1) + "/" + total + ": " + agent + "\n\n"
                    + "Workflow: " + scope.workflowId() + "\nRun:      " + runId + "\n";
            var committed = gitMcp.commit(ws.repositoryUrl(), message, AUTHOR, AUTHOR_EMAIL, ws.workspaceKey());
            if (!committed.success()) {
                if (committed.message() != null && committed.message().startsWith("Nothing to commit")) {
                    return Optional.of("Checkpoint: nothing to commit after " + agent);
                }
                return failed(ws, runId, agent, "commit failed: " + committed.message());
            }
            var pushed = gitMcp.push(ws.repositoryUrl(), ws.branch(), ws.workspaceKey());
            if (!pushed.success()) {
                return failed(ws, runId, agent, "committed " + shortHash(committed.commitHash())
                        + " but push failed: " + pushed.message());
            }
            log.info("workflow.run.checkpoint runId={} workspace={} agent={} commit={}", runId, ws.workspaceKey(),
                    agent, shortHash(committed.commitHash()));
            return Optional.of("Checkpoint " + shortHash(committed.commitHash()) + " pushed to " + ws.branch());
        } catch (LiteLlmMcpToolClient.McpCallException e) {
            return failed(ws, runId, agent, "git workspace unavailable");
        } catch (RuntimeException e) {
            return failed(ws, runId, agent, e.getClass().getSimpleName());
        }
    }

    private static Optional<String> failed(GitWorkspaceBinding ws, String runId, String agent, String reason) {
        log.warn("workflow.run.checkpoint.failed runId={} workspace={} agent={} reason={}", runId, ws.workspaceKey(),
                agent, reason);
        return Optional.of("Checkpoint after " + agent + " failed: " + reason);
    }

    /** {@code a2a:litellm:qa-engineer} → {@code qa-engineer}; anything unexpected → safe characters only. */
    static String fileSafe(String agentKey) {
        String last = agentKey == null ? "" : agentKey.substring(agentKey.lastIndexOf(':') + 1).toLowerCase();
        String safe = last.replaceAll("[^a-z0-9-]", "-");
        return safe.isBlank() ? "agent" : safe;
    }

    private static String shortHash(String hash) {
        return hash == null ? "?" : hash.substring(0, Math.min(7, hash.length()));
    }
}
