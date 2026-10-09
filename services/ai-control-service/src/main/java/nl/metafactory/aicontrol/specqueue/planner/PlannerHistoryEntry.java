package nl.metafactory.aicontrol.specqueue.planner;

import nl.metafactory.aicontrol.model.SpecQueueFailureReason;
import nl.metafactory.aicontrol.model.SpecQueueItemStatus;

public record PlannerHistoryEntry(SpecQueueItemStatus status, SpecQueueFailureReason failureReason) {}
