package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.generated.api.WorkspaceApi;
import nl.metafactory.aicontrol.model.*;
import nl.metafactory.aicontrol.service.ModelRoutingService;
import nl.metafactory.aicontrol.service.WorkspaceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WorkspaceController implements WorkspaceApi {
    private final WorkspaceService workspaceService;
    private final ModelRoutingService modelRoutingService;

    public WorkspaceController(WorkspaceService workspaceService, ModelRoutingService modelRoutingService) {
        this.workspaceService = workspaceService;
        this.modelRoutingService = modelRoutingService;
    }

    @Override
    public ResponseEntity<Workspace> workspace(String customerId) {
        return ResponseEntity.ok(workspaceService.loadWorkspace(customerId));
    }

    @Override
    public ResponseEntity<ModelRouteDecision> decideModelRoute(ModelRouteRequest request) {
        return ResponseEntity.ok(modelRoutingService.decide(request));
    }
}
