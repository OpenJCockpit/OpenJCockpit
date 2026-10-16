package nl.metafactory.agents.api;

import nl.metafactory.agents.model.AgentRun;
import nl.metafactory.agents.orchestration.AgentOrchestrator;
import nl.metafactory.agents.orchestration.AgentRunOutputs;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
/** Workflow runs: status, stop and the full agent outputs. Runs are started only through workflows. */
public class AgentRunController {
    private final AgentOrchestrator orchestrator;
    private final AgentRunOutputs outputs;

    public AgentRunController(AgentOrchestrator orchestrator, AgentRunOutputs outputs) {
        this.orchestrator = orchestrator;
        this.outputs = outputs;
    }

    /** Full output of each catalog agent of a workflow run, in execution order. */
    @GetMapping("/agent-runs/{runId}/outputs")
    public List<AgentRunOutputs.Output> outputs(@PathVariable String runId) {
        return outputs.find(runId);
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
