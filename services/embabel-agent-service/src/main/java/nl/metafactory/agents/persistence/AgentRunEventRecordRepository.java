package nl.metafactory.agents.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentRunEventRecordRepository extends JpaRepository<AgentRunEventRecord, AgentRunEventId> {

    List<AgentRunEventRecord> findByRunIdOrderBySequenceNoAsc(String runId);

    long countByRunId(String runId);

    @Query("select max(e.sequenceNo) from AgentRunEventRecord e where e.runId = :runId")
    Integer findMaxSequenceNoByRunId(@Param("runId") String runId);
}
