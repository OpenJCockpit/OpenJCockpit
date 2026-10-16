package nl.metafactory.agents.definition.runtime;

import nl.metafactory.agents.definition.resolver.AgentDefinitionResolver;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * Entry point for one configuration-driven agent run: resolves the definition exactly once,
 * freezes it in an immutable {@link RunContext}, and keeps tenant/agent/version/trace in the MDC for
 * every log line of the run.
 */
public class AgentRunService {

    static final String[] MDC_KEYS = {"runId", "traceId", "tenantId", "agentId", "agentVersion"};

    private final AgentDefinitionResolver resolver;
    private final ConfiguredDefinitionAgent agent;

    public AgentRunService(AgentDefinitionResolver resolver, ConfiguredDefinitionAgent agent) {
        this.resolver = resolver;
        this.agent = agent;
    }

    public ConfiguredDefinitionAgent.Response run(String tenantId, String agentId, String principal, String traceparent,
                                                  String message) {
        return run(tenantId, agentId, principal, traceparent, message, null);
    }

    /** As {@link #run(String, String, String, String, String)}, as part of a workflow run. */
    public ConfiguredDefinitionAgent.Response run(String tenantId, String agentId, String principal, String traceparent,
                                                  String message,
                                                  nl.metafactory.agents.workflowrun.WorkflowRunScope workflow) {
        String traceId = TraceIds.fromTraceparentOrNew(traceparent);
        String runId = UUID.randomUUID().toString();
        MDC.put("runId", runId);
        MDC.put("traceId", traceId);
        try {
            var prepared = resolver.resolve(tenantId, agentId);
            var d = prepared.definition();
            var run = new RunContext(runId, traceId, d.tenantId(), d.agentId(), d.version(), d.contentHash(), principal,
                    workflow);
            MDC.put("tenantId", run.tenantId());
            MDC.put("agentId", run.agentId());
            MDC.put("agentVersion", run.version());
            return agent.respond(new ConfiguredDefinitionAgent.Request(prepared, run, message));
        } finally {
            for (String key : MDC_KEYS) {
                MDC.remove(key);
            }
        }
    }
}
