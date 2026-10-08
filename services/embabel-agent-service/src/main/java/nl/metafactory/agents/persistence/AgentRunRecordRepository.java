package nl.metafactory.agents.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRunRecordRepository extends JpaRepository<AgentRunRecord, String> {
}
