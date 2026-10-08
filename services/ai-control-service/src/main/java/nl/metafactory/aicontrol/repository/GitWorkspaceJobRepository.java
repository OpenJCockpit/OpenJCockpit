package nl.metafactory.aicontrol.repository;

import nl.metafactory.aicontrol.model.GitWorkspaceJob;
import nl.metafactory.aicontrol.model.GitWorkspaceJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface GitWorkspaceJobRepository extends JpaRepository<GitWorkspaceJob, UUID> {

    List<GitWorkspaceJob> findAllByProjectIdOrderByCreatedAtDesc(UUID projectId);

    List<GitWorkspaceJob> findAllByStatusAndCreatedAtBefore(GitWorkspaceJobStatus status, Instant before);
}
