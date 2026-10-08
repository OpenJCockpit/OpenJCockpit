package nl.metafactory.aicontrol.specqueue.persistence;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SpecQueueRepository extends JpaRepository<SpecQueue, UUID> {

    /** The queue row is the per-project mutex; waits at most 5 s for the lock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select q from SpecQueue q where q.projectId = :projectId")
    Optional<SpecQueue> findByProjectIdForUpdate(@Param("projectId") UUID projectId);
}
