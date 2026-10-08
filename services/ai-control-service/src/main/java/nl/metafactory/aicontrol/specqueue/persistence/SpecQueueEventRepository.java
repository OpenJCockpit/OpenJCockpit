package nl.metafactory.aicontrol.specqueue.persistence;

import nl.metafactory.aicontrol.specqueue.domain.SpecQueueEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

/** Append-only audit log: deliberately not a JpaRepository, so there is no delete or bulk update. */
public interface SpecQueueEventRepository extends Repository<SpecQueueEvent, UUID> {

    SpecQueueEvent save(SpecQueueEvent event);

    List<SpecQueueEvent> findByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);

    List<SpecQueueEvent> findByItemIdOrderByCreatedAtAsc(UUID itemId);
}
