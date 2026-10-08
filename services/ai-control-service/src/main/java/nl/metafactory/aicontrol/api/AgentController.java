package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.client.AgentDefinitionDto;
import nl.metafactory.aicontrol.client.AgentRunDto;
import nl.metafactory.aicontrol.client.AgentRunRequestDto;
import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.generated.api.AgentApi;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
public class AgentController implements AgentApi {

    private final EmbabelAgentClient embabelAgentClient;

    public AgentController(EmbabelAgentClient embabelAgentClient) {
        this.embabelAgentClient = embabelAgentClient;
    }

    @Override
    public ResponseEntity<List<AgentDefinitionDto>> listAgents() {
        return ResponseEntity.ok(embabelAgentClient.getAgentDefinitions());
    }

    @Override
    public ResponseEntity<AgentRunDto> startAgentRun(AgentRunRequestDto request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(embabelAgentClient.startAgentRun(request));
    }

    @Override
    public ResponseEntity<AgentRunDto> getAgentRun(String runId) {
        AgentRunDto run = embabelAgentClient.getLatestRun(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found: " + runId));
        return ResponseEntity.ok(run);
    }

    @Override
    public ResponseEntity<Void> stopAgentRun(String runId) {
        embabelAgentClient.stopAgentRun(runId);
        return ResponseEntity.noContent().build();
    }
}
