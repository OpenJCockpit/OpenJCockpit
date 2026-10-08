package nl.metafactory.agents.api;

import nl.metafactory.agents.model.AgentDefinition;
import nl.metafactory.agents.model.AgentRun;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.orchestration.AgentOrchestrator;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AgentRunController {
    private final AgentOrchestrator orchestrator;

    public AgentRunController(AgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @GetMapping("/agents")
    public List<AgentDefinition> agents() {
        return orchestrator.availableAgents();
    }

    @PostMapping("/agent-runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AgentRun start(@RequestBody AgentRunRequest request) {
        if (request.agentIds() == null || request.agentIds().isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "agentIds must not be null or empty");
        }
        // A caller must never be able to forge history attribution (startedBy) or inject a run
        // into another workflow's history (workflowId) by calling this legacy, unscoped endpoint
        // directly — those fields, plus the workflow-orb-only identity/ancestry fields (initiator,
        // chainAncestry), are always server-derived (see WorkflowExecutionService), never
        // client-supplied here.
        var sanitizedRequest = new AgentRunRequest(request.customerId(), request.specFile(),
                request.agentIds(), request.requestedBy(), request.repositoryUrl(), request.gitUsername(),
                request.gitToken(), request.approvalGate(), request.baseBranch(), null, null, null, null);
        return orchestrator.start(sanitizedRequest);
    }

    @GetMapping("/agent-runs/{runId}")
    public AgentRun get(@PathVariable String runId) {
        return orchestrator.get(runId);
    }

    @DeleteMapping("/agent-runs/{runId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void stop(@PathVariable String runId) {
        orchestrator.stop(runId);
    }
}
