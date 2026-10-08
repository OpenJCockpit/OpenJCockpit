package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.generated.api.GitCheckApi;
import nl.metafactory.aicontrol.model.ProjectGitStatusDto;
import nl.metafactory.aicontrol.service.GitConnectivityService;
import nl.metafactory.aicontrol.service.ProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class GitCheckController implements GitCheckApi {

    private final GitConnectivityService connectivityService;
    private final ProjectService projectService;

    public GitCheckController(GitConnectivityService connectivityService, ProjectService projectService) {
        this.connectivityService = connectivityService;
        this.projectService = projectService;
    }

    @Override
    public ResponseEntity<ProjectGitStatusDto> triggerGitCheck(UUID id) {
        connectivityService.checkProjectGitAccess(id);
        return ResponseEntity.ok(toStatusDto(projectService.findById(id)));
    }

    @Override
    public ResponseEntity<ProjectGitStatusDto> getGitStatus(UUID id) {
        return ResponseEntity.ok(toStatusDto(projectService.findById(id)));
    }

    private ProjectGitStatusDto toStatusDto(nl.metafactory.aicontrol.model.ProjectDto p) {
        return new ProjectGitStatusDto(p.id(), p.gitStatus(), p.gitStatusCheckedAt(), p.gitStatusMessage());
    }
}
