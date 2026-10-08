package nl.metafactory.aicontrol.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowExecutionPageDto(List<WorkflowExecutionSummaryDto> items, int limit,
                                       int offset, long total, boolean hasMore) {}
