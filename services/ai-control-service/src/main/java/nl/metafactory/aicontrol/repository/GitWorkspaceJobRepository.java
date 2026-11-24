package nl.metafactory.aicontrol.repository;

import nl.metafactory.aicontrol.model.GitWorkspaceJob;
import nl.metafactory.aicontrol.model.GitWorkspaceJobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GitWorkspaceJobRepository extends JpaRepository<GitWorkspaceJob, UUID> {

    List<GitWorkspaceJob> findAllByProjectIdOrderByCreatedAtDesc(UUID projectId);

    Page<GitWorkspaceJob> findAllByProjectIdAndRunIdIsNotNullOrderByCreatedAtDesc(UUID projectId, Pageable pageable);

    Optional<GitWorkspaceJob> findFirstByProjectIdAndRunIdOrderByCreatedAtDesc(UUID projectId, String runId);

    Optional<GitWorkspaceJob> findFirstByProjectIdAndRunIdIsNotNullOrderByCreatedAtDesc(UUID projectId);

    List<GitWorkspaceJob> findAllByStatusAndCreatedAtBefore(GitWorkspaceJobStatus status, Instant before);
}
