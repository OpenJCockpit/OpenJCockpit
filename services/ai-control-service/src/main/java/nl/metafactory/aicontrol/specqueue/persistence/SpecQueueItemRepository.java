package nl.metafactory.aicontrol.specqueue.persistence;

import nl.metafactory.aicontrol.model.SpecQueueItemStatus;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpecQueueItemRepository extends JpaRepository<SpecQueueItem, UUID> {

    Optional<SpecQueueItem> findByIdAndProjectId(UUID id, UUID projectId);

    List<SpecQueueItem> findByProjectIdOrderByPositionAsc(UUID projectId);

    List<SpecQueueItem> findByProjectIdAndStatusInOrderByPositionAsc(UUID projectId, Collection<SpecQueueItemStatus> statuses);

    Optional<SpecQueueItem> findFirstByProjectIdOrderByPositionDesc(UUID projectId);

    Optional<SpecQueueItem> findFirstByProjectIdOrderByPositionAsc(UUID projectId);

    Optional<SpecQueueItem> findByProjectIdAndActiveSlot(UUID projectId, Short activeSlot);

    Optional<SpecQueueItem> findByProjectIdAndOpenSpecKey(UUID projectId, String openSpecKey);

    @Query("select distinct i.projectId from SpecQueueItem i where i.status in :statuses")
    List<UUID> findProjectIdsByStatusIn(@Param("statuses") Collection<SpecQueueItemStatus> statuses);
}
