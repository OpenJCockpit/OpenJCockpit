package nl.metafactory.agents.workflow.agents;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** PostgreSQL implementation of {@link WorkflowAgentLinks} (table {@code workflow_agent_refs}, V4). */
@Component
public class JdbcWorkflowAgentLinks implements WorkflowAgentLinks {

    private final JdbcClient jdbc;

    public JdbcWorkflowAgentLinks(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<String> find(String workflowId) {
        if (workflowId == null) {
            return List.of();
        }
        return jdbc.sql("""
                        SELECT source, provider, agent_id FROM workflow_agent_refs
                        WHERE workflow_id = :workflow ORDER BY position""")
                .param("workflow", workflowId)
                .query((rs, n) -> new AgentRef(AgentRef.Source.valueOf(rs.getString(1)), rs.getString(2),
                        rs.getString(3)).key())
                .list();
    }

    @Override
    @Transactional
    public void replace(String workflowId, List<String> keys) {
        var refs = keys.stream().map(k -> AgentRef.parse(k)
                .orElseThrow(() -> new IllegalArgumentException("not an agent reference: " + k))).toList();
        delete(workflowId);
        for (int i = 0; i < refs.size(); i++) {
            var ref = refs.get(i);
            jdbc.sql("""
                            INSERT INTO workflow_agent_refs (workflow_id, position, source, provider, agent_id)
                            VALUES (:workflow, :position, :source, :provider, :agent)""")
                    .param("workflow", workflowId).param("position", i).param("source", ref.source().name())
                    .param("provider", ref.provider()).param("agent", ref.agentId())
                    .update();
        }
    }

    @Override
    public void delete(String workflowId) {
        jdbc.sql("DELETE FROM workflow_agent_refs WHERE workflow_id = :workflow").param("workflow", workflowId).update();
    }
}
