package nl.metafactory.agents.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentRunArtifactRecordRepository extends JpaRepository<AgentRunArtifactRecord, AgentRunArtifactId> {

    List<AgentRunArtifactRecord> findByRunIdOrderBySequenceNoAsc(String runId);

    long countByRunId(String runId);

    @Query("select max(a.sequenceNo) from AgentRunArtifactRecord a where a.runId = :runId")
    Integer findMaxSequenceNoByRunId(@Param("runId") String runId);
}
