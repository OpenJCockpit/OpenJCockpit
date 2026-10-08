package nl.metafactory.agents.model;

import java.util.List;

/** Page envelope of {@link AgentRunSummary} rows, replacing the deleted {@code AgentRunPage}
 * (which held full {@code AgentRun} items) — a page of summaries is a distinct type from a page
 * of full runs. */
public record AgentRunSummaryPage(List<AgentRunSummary> items, int limit, int offset, long total,
                                   boolean hasMore) {}
