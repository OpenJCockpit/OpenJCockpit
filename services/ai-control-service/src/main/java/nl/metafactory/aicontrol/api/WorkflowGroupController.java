package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.client.WorkflowGroupDto;
import nl.metafactory.aicontrol.generated.api.WorkflowGroupApi;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
public class WorkflowGroupController implements WorkflowGroupApi {

    private final EmbabelAgentClient client;

    public WorkflowGroupController(EmbabelAgentClient client) {
        this.client = client;
    }

    // NOTE: operationId "list" is a reserved word for the spring generator template
    // and is auto-renamed to "callList" in the generated WorkflowGroupApi interface.
    @Override
    public ResponseEntity<List<WorkflowGroupDto>> callList() {
        return ResponseEntity.ok(client.listWorkflowGroups());
    }

    @Override
    public ResponseEntity<WorkflowGroupDto> get(String id) {
        WorkflowGroupDto group = client.getWorkflowGroup(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow group not found: " + id));
        return ResponseEntity.ok(group);
    }

    @Override
    public ResponseEntity<WorkflowGroupDto> create(WorkflowGroupDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(client.createWorkflowGroup(request));
    }

    @Override
    public ResponseEntity<WorkflowGroupDto> update(String id, WorkflowGroupDto request) {
        return ResponseEntity.ok(client.updateWorkflowGroup(id, request));
    }

    @Override
    public ResponseEntity<Void> delete(String id) {
        client.deleteWorkflowGroup(id);
        return ResponseEntity.noContent().build();
    }
}
