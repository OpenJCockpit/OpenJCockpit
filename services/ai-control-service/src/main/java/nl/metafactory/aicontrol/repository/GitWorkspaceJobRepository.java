package nl.metafactory.aicontrol.repository;

import nl.metafactory.aicontrol.model.GitWorkspaceJob;
import nl.metafactory.aicontrol.model.GitWorkspaceJobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** Run ids of a project in a time window, oldest first. A run can have several jobs, so ids may repeat. */
    @Query("select j.runId from GitWorkspaceJob j where j.projectId = :projectId and j.runId is not null "
            + "and j.createdAt >= :from and j.createdAt <= :to order by j.createdAt asc, j.id asc")
    List<String> findRunIds(@Param("projectId") UUID projectId, @Param("from") Instant from,
                            @Param("to") Instant to, Pageable pageable);

    @Query("select j.runId from GitWorkspaceJob j where j.projectId = :projectId and j.runId is not null "
            + "and j.workflowId = :workflowId and j.createdAt >= :from and j.createdAt <= :to "
            + "order by j.createdAt asc, j.id asc")
    List<String> findRunIdsByWorkflow(@Param("projectId") UUID projectId, @Param("workflowId") String workflowId,
                                      @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);
}
