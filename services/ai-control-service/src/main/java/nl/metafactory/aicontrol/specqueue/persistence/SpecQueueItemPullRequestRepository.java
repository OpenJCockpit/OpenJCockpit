package nl.metafactory.aicontrol.specqueue.persistence;

import nl.metafactory.aicontrol.specqueue.domain.SpecQueueItemPullRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpecQueueItemPullRequestRepository extends JpaRepository<SpecQueueItemPullRequest, UUID> {

    List<SpecQueueItemPullRequest> findByItemIdOrderByCreatedAtAsc(UUID itemId);
}
