package nl.metafactory.agents.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

public interface AgentRunHistoryQueryRepository extends JpaRepository<AgentRunRecord, String> {

    @Query("select new nl.metafactory.agents.persistence.AgentRunSummaryProjection(" +
           "r.runId, r.workflowId, r.status, r.startedAt, r.completedAt, r.startedBy) " +
           "from AgentRunRecord r where r.workflowId = :workflowId " +
           "order by r.startedAt desc, r.runId desc")
    List<AgentRunSummaryProjection> findSummariesByWorkflowId(@Param("workflowId") String workflowId, Pageable pageable);

    @Query("select count(r) from AgentRunRecord r where r.workflowId = :workflowId")
    long countByWorkflowId(@Param("workflowId") String workflowId);

    @Query("""
            select new nl.metafactory.agents.persistence.WorkflowLastExecutionProjection(
                    r.workflowId, r.status, r.startedAt)
            from AgentRunRecord r
            where r.workflowId in :workflowIds
              and not exists (
                    select r2.runId from AgentRunRecord r2
                    where r2.workflowId = r.workflowId
                      and (r2.startedAt > r.startedAt
                           or (r2.startedAt = r.startedAt and r2.runId > r.runId)))
            """)
    List<WorkflowLastExecutionProjection> findLatestRunPerWorkflow(
            @Param("workflowIds") Collection<String> workflowIds);
}
