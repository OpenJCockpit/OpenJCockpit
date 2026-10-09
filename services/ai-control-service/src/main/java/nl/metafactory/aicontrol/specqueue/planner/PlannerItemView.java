package nl.metafactory.aicontrol.specqueue.planner;

import java.util.UUID;

public record PlannerItemView(UUID id, String specFile, String workflowId, int position,
                              boolean autoMerge, boolean effectiveAutoMerge) {}
