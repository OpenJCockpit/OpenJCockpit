package nl.metafactory.aicontrol.specqueue.planner;

import nl.metafactory.aicontrol.model.SpecQueueState;

import java.util.UUID;

public record PlannerProjectView(UUID projectId, SpecQueueState state, boolean autoMergeAllowed) {}
