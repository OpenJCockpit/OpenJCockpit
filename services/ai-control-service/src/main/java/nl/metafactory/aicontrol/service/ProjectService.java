package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.model.GitStatus;
import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.model.ProjectDto;
import nl.metafactory.aicontrol.model.ProjectRequest;
import nl.metafactory.aicontrol.repository.ProjectGitCredentialRepository;
import nl.metafactory.aicontrol.repository.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ProjectService {

    private final ProjectRepository repository;
    private final ProjectGitCredentialRepository credentialRepository;

    public ProjectService(ProjectRepository repository, ProjectGitCredentialRepository credentialRepository) {
        this.repository = repository;
        this.credentialRepository = credentialRepository;
    }

    @Transactional(readOnly = true)
    public List<ProjectDto> listActive() {
        return repository.findAllByActiveOrderByNameAsc((short) 1).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProjectDto> listAll() {
        return repository.findAllByOrderByNameAsc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectDto findById(UUID id) {
        return toDto(getOrThrow(id));
    }

    public ProjectDto create(ProjectRequest req) {
        var project = new Project();
        applyRequest(project, req);
        return toDto(repository.save(project));
    }

    public ProjectDto update(UUID id, ProjectRequest req) {
        var project = getOrThrow(id);
        applyRequest(project, req);
        return toDto(repository.save(project));
    }

    public void setActive(UUID id, short active) {
        var project = getOrThrow(id);
        project.setActive(active);
        repository.save(project);
    }

    public void setNewProject(UUID id, short newProject) {
        var project = getOrThrow(id);
        project.setNewProject(newProject);
        repository.save(project);
    }

    public void updateGitStatus(UUID id, GitStatus status, Instant checkedAt, String message) {
        var project = getOrThrow(id);
        project.setGitStatus(status);
        project.setGitStatusCheckedAt(checkedAt);
        project.setGitStatusMessage(message);
        repository.save(project);
    }

    Project getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found: " + id));
    }

    private void applyRequest(Project project, ProjectRequest req) {
        project.setName(req.name());
        project.setCustomerId(req.customerId());
        project.setGitUrl(req.gitUrl());
        project.setDescription(req.description());
        project.setDefaultBranch(req.defaultBranch());
        project.setEnvironment(req.environment());
        project.setOwner(req.owner());
        project.setNewProject(req.newProject());
    }

    ProjectDto toDto(Project p) {
        boolean hasCredentials = credentialRepository
                .findFirstByProjectIdAndActiveOrderByCreatedAtDesc(p.getId(), (short) 1)
                .isPresent();
        return new ProjectDto(
                p.getId(),
                p.getName(),
                p.getCustomerId(),
                p.getGitUrl(),
                p.getDescription(),
                p.getDefaultBranch(),
                p.getEnvironment(),
                p.getOwner(),
                p.getActive(),
                p.getNewProject(),
                p.getGitStatus(),
                p.getGitStatusCheckedAt(),
                p.getGitStatusMessage(),
                hasCredentials,
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
