package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.generated.api.ProjectApi;
import nl.metafactory.aicontrol.model.NewProjectFlagRequest;
import nl.metafactory.aicontrol.model.ProjectDto;
import nl.metafactory.aicontrol.model.ProjectRequest;
import nl.metafactory.aicontrol.service.ProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// Admin endpoints (create/update/activate/deactivate) are open to all authenticated users.
// TODO: Add @PreAuthorize("hasRole('METAFACTORY_ADMIN')") and @EnableMethodSecurity in SecurityConfig
//       once Keycloak realm roles are configured. See ProjectService for context.
@RestController
public class ProjectController implements ProjectApi {

    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<List<ProjectDto>> listActive() {
        return ResponseEntity.ok(service.listActive());
    }

    @Override
    public ResponseEntity<List<ProjectDto>> listAll() {
        return ResponseEntity.ok(service.listAll());
    }

    @Override
    public ResponseEntity<ProjectDto> create(ProjectRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @Override
    public ResponseEntity<ProjectDto> update(UUID id, ProjectRequest req) {
        return ResponseEntity.ok(service.update(id, req));
    }

    @Override
    public ResponseEntity<Void> activate(UUID id) {
        service.setActive(id, (short) 1);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> deactivate(UUID id) {
        service.setActive(id, (short) 0);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> setNewProject(UUID id, NewProjectFlagRequest req) {
        service.setNewProject(id, req.newProject());
        return ResponseEntity.noContent().build();
    }
}