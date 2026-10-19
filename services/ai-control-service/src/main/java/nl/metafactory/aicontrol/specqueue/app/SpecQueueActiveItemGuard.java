package nl.metafactory.aicontrol.specqueue.app;

import nl.metafactory.aicontrol.specqueue.persistence.SpecQueueItemRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Blocks manual workflow starts while a queue item of the project holds the active slot. */
@Component
public class SpecQueueActiveItemGuard {

    private static final Short ACTIVE_SLOT = 1;

    private final SpecQueueItemRepository items;

    public SpecQueueActiveItemGuard(SpecQueueItemRepository items) {
        this.items = items;
    }

    public void assertNoActiveItem(UUID projectId) {
        if (projectId == null) {
            return;
        }
        items.findByProjectIdAndActiveSlot(projectId, ACTIVE_SLOT).ifPresent(item -> {
            throw new SpecQueueException(SpecQueueException.Code.SPEC_QUEUE_ITEM_ACTIVE,
                    "A spec queue item is active for this project: specFile=" + item.getSpecFile()
                            + ", status=" + item.getStatus() + ", itemId=" + item.getId());
        });
    }
}
