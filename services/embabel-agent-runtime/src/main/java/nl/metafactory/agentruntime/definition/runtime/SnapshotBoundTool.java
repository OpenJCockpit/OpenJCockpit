package nl.metafactory.agentruntime.definition.runtime;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.api.tool.ToolCallContext;
import nl.metafactory.agentruntime.definition.tool.RegisteredAgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Call-time re-check of a bound tool against the run's snapshot. Bind-time authorisation already
 * happened in the resolver; this guards against a tool reaching the model by any other path.
 * Tool inputs and outputs are never logged.
 */
final class SnapshotBoundTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(SnapshotBoundTool.class);

    private final RegisteredAgentTool registered;
    private final Set<String> allowedToolIds;
    private final ToolCallAuthorizer authorizer;
    private final RunContext run;

    SnapshotBoundTool(RegisteredAgentTool registered, Set<String> allowedToolIds, ToolCallAuthorizer authorizer,
                      RunContext run) {
        this.registered = registered;
        this.allowedToolIds = Set.copyOf(allowedToolIds);
        this.authorizer = authorizer;
        this.run = run;
    }

    @Override
    public Definition getDefinition() {
        return registered.tool().getDefinition();
    }

    @Override
    public Metadata getMetadata() {
        return registered.tool().getMetadata();
    }

    @Override
    public Result call(String input) {
        return call(input, null);
    }

    @Override
    public Result call(String input, ToolCallContext context) {
        if (!allowedToolIds.contains(registered.id())) {
            log.warn("agent.tool.denied reason=not-in-snapshot tool={} {}", registered.id(), run.logFields());
            return Result.error("tool " + registered.id() + " is not permitted for this run");
        }
        var decision = authorizer.authorize(run, registered.id());
        if (!decision.allowed()) {
            log.warn("agent.tool.denied reason=policy tool={} {}", registered.id(), run.logFields());
            return Result.error("tool " + registered.id() + " was denied by policy");
        }
        log.info("agent.tool.invoked tool={} {}", registered.id(), run.logFields());
        if (registered.tool() instanceof nl.metafactory.agentruntime.definition.tool.RunScopedTool scoped) {
            return scoped.call(input, run);
        }
        return context == null ? registered.tool().call(input) : registered.tool().call(input, context);
    }
}
