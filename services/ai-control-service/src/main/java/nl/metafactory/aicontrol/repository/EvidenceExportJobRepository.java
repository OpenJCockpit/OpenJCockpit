package nl.metafactory.aicontrol.repository;

import nl.metafactory.aicontrol.model.EvidenceExportJobEntity;
import nl.metafactory.aicontrol.model.EvidenceExportJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * State changes are conditional updates on the current status, so a cancel and the worker's own
 * transitions cannot overwrite each other.
 */
public interface EvidenceExportJobRepository extends JpaRepository<EvidenceExportJobEntity, UUID> {

    Optional<EvidenceExportJobEntity> findByIdAndProjectId(UUID id, UUID projectId);

    long countByProjectIdAndStatusIn(UUID projectId, Collection<EvidenceExportJobStatus> statuses);

    @Query("select j.status from EvidenceExportJobEntity j where j.id = :id")
    Optional<EvidenceExportJobStatus> findStatusById(@Param("id") UUID id);

    @Query("select coalesce(sum(j.sizeBytes), 0) from EvidenceExportJobEntity j where j.filePath is not null")
    long sumStoredBytes();

    List<EvidenceExportJobEntity> findAllByStatusAndExpiresAtBeforeAndFilePathIsNotNull(
            EvidenceExportJobStatus status, Instant before);

    List<EvidenceExportJobEntity> findAllByStatusInAndUpdatedAtBefore(
            Collection<EvidenceExportJobStatus> statuses, Instant before);

    @Transactional
    @Modifying
    @Query("update EvidenceExportJobEntity j set j.status = 'RUNNING', j.updatedAt = :now "
            + "where j.id = :id and j.status = 'QUEUED'")
    int markRunning(@Param("id") UUID id, @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("update EvidenceExportJobEntity j set j.runsTotal = :total, j.runsDone = 0, j.updatedAt = :now "
            + "where j.id = :id and j.status = 'RUNNING'")
    int updateTotal(@Param("id") UUID id, @Param("total") int total, @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("update EvidenceExportJobEntity j set j.runsDone = :done, j.updatedAt = :now "
            + "where j.id = :id and j.status = 'RUNNING'")
    int updateProgress(@Param("id") UUID id, @Param("done") int done, @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("update EvidenceExportJobEntity j set j.status = 'DONE', j.filename = :filename, "
            + "j.sizeBytes = :size, j.filePath = :path, j.expiresAt = :expiresAt, j.updatedAt = :now "
            + "where j.id = :id and j.status = 'RUNNING'")
    int markDone(@Param("id") UUID id, @Param("filename") String filename, @Param("size") long size,
                 @Param("path") String path, @Param("expiresAt") Instant expiresAt, @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("update EvidenceExportJobEntity j set j.status = 'FAILED', j.error = :error, j.updatedAt = :now "
            + "where j.id = :id and j.status in ('QUEUED', 'RUNNING')")
    int markFailed(@Param("id") UUID id, @Param("error") String error, @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("update EvidenceExportJobEntity j set j.status = 'CANCELLED', j.updatedAt = :now "
            + "where j.id = :id and j.status in ('QUEUED', 'RUNNING')")
    int markCancelled(@Param("id") UUID id, @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("update EvidenceExportJobEntity j set j.filePath = null, j.updatedAt = :now where j.id = :id")
    int clearFile(@Param("id") UUID id, @Param("now") Instant now);
}
