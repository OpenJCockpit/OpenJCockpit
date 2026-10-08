package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.model.GitStatus;
import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.model.ProjectRequest;
import nl.metafactory.aicontrol.repository.ProjectGitCredentialRepository;
import nl.metafactory.aicontrol.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectServiceTest {

    private ProjectRepository repository;
    private ProjectGitCredentialRepository credentialRepository;
    private ProjectService service;

    @BeforeEach
    void setUp() {
        repository = mock(ProjectRepository.class);
        credentialRepository = mock(ProjectGitCredentialRepository.class);
        service = new ProjectService(repository, credentialRepository);
        when(credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(any(), anyShort()))
                .thenReturn(Optional.empty());
    }

    @Test
    void listActiveReturnsOnlyActiveProjects() {
        when(repository.findAllByActiveOrderByNameAsc((short) 1))
                .thenReturn(List.of(project(UUID.randomUUID(), "Alpha", (short) 1)));

        var result = service.listActive();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Alpha");
        assertThat(result.get(0).active()).isEqualTo((short) 1);
    }

    @Test
    void listAllReturnsAllProjects() {
        var active = project(UUID.randomUUID(), "Alpha", (short) 1);
        var inactive = project(UUID.randomUUID(), "Beta", (short) 0);
        when(repository.findAllByOrderByNameAsc()).thenReturn(List.of(active, inactive));

        var result = service.listAll();

        assertThat(result).hasSize(2);
    }

    @Test
    void findByIdReturnsProject() {
        var id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(project(id, "Gamma", (short) 1)));

        var dto = service.findById(id);

        assertThat(dto.id()).isEqualTo(id);
        assertThat(dto.name()).isEqualTo("Gamma");
    }

    @Test
    void findByIdThrowsNotFoundForUnknownId() {
        var id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createPersistsAndReturnsDto() {
        var req = new ProjectRequest("New", "cust-id", "https://git.example.com", "desc", "main", "prod", "Alice", (short) 0);
        var saved = project(UUID.randomUUID(), "New", (short) 1);
        saved.setCustomerId("cust-id");
        saved.setGitUrl("https://git.example.com");
        saved.setDescription("desc");
        saved.setDefaultBranch("main");
        saved.setEnvironment("prod");
        saved.setOwner("Alice");
        when(repository.save(any())).thenReturn(saved);

        var dto = service.create(req);

        assertThat(dto.name()).isEqualTo("New");
        assertThat(dto.customerId()).isEqualTo("cust-id");
        assertThat(dto.gitUrl()).isEqualTo("https://git.example.com");
        assertThat(dto.description()).isEqualTo("desc");
        assertThat(dto.defaultBranch()).isEqualTo("main");
        assertThat(dto.environment()).isEqualTo("prod");
        assertThat(dto.owner()).isEqualTo("Alice");
        verify(repository).save(any());
    }

    @Test
    void updateAppliesChangesAndReturnsDto() {
        var id = UUID.randomUUID();
        var existing = project(id, "Old", (short) 1);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new ProjectRequest("Updated", null, null, null, null, null, null, (short) 0);
        var dto = service.update(id, req);

        assertThat(dto.name()).isEqualTo("Updated");
    }

    @Test
    void updateThrowsNotFoundForUnknownId() {
        var id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        var req = new ProjectRequest("X", null, null, null, null, null, null, (short) 0);
        assertThatThrownBy(() -> service.update(id, req))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void setActiveSetsActiveField() {
        var id = UUID.randomUUID();
        var project = project(id, "Zeta", (short) 1);
        when(repository.findById(id)).thenReturn(Optional.of(project));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.setActive(id, (short) 0);

        assertThat(project.getActive()).isZero();
        verify(repository).save(project);
    }

    @Test
    void setActiveThrowsNotFoundForUnknownId() {
        var id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setActive(id, (short) 0))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void setNewProjectSetsNewProjectField() {
        var id = UUID.randomUUID();
        var project = project(id, "Eta", (short) 1);
        when(repository.findById(id)).thenReturn(Optional.of(project));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.setNewProject(id, (short) 1);

        assertThat(project.getNewProject()).isEqualTo((short) 1);
        verify(repository).save(project);
    }

    @Test
    void setNewProjectThrowsNotFoundForUnknownId() {
        var id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setNewProject(id, (short) 1))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void updateGitStatusUpdatesAllGitFields() {
        var id = UUID.randomUUID();
        var project = project(id, "Theta", (short) 1);
        when(repository.findById(id)).thenReturn(Optional.of(project));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var now = Instant.now();
        service.updateGitStatus(id, GitStatus.ACCESSIBLE, now, "Reachable");

        assertThat(project.getGitStatus()).isEqualTo(GitStatus.ACCESSIBLE);
        assertThat(project.getGitStatusCheckedAt()).isEqualTo(now);
        assertThat(project.getGitStatusMessage()).isEqualTo("Reachable");
    }

    @Test
    void updateGitStatusThrowsNotFoundForUnknownId() {
        var id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateGitStatus(id, GitStatus.UNKNOWN, Instant.now(), null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void toDtoIncludesHasCredentialsTrueWhenCredentialsExist() {
        var id = UUID.randomUUID();
        var project = project(id, "Iota", (short) 1);
        when(repository.findById(id)).thenReturn(Optional.of(project));
        when(credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1))
                .thenReturn(Optional.of(new nl.metafactory.aicontrol.model.ProjectGitCredential()));

        var dto = service.findById(id);

        assertThat(dto.hasCredentials()).isTrue();
    }

    @Test
    void toDtoIncludesHasCredentialsFalseWhenNoCredentials() {
        var id = UUID.randomUUID();
        var project = project(id, "Kappa", (short) 1);
        when(repository.findById(id)).thenReturn(Optional.of(project));
        when(credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1))
                .thenReturn(Optional.empty());

        var dto = service.findById(id);

        assertThat(dto.hasCredentials()).isFalse();
    }

    private Project project(UUID id, String name, short active) {
        var p = new Project();
        p.setId(id);
        p.setName(name);
        p.setActive(active);
        p.setCreatedAt(Instant.now());
        p.setUpdatedAt(Instant.now());
        return p;
    }
}
