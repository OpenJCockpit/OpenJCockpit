package nl.metafactory.agents.runtime;

import nl.metafactory.agents.workflowrun.WorkflowRunScope;
import nl.metafactory.agents.workflowrun.WorkflowRunScopes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The runtime's view of this service over HTTP: real security chain, real tenant-binding table (H2), real
 * run registry and the real policy service (OPA disabled in tests, so decisions are SKIPPED_DISABLED/allow).
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AgentRuntimeManagementApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;
    @Autowired WorkflowRunScopes runScopes;

    @Test
    void theRuntimeReadsBindingsRunsAndDecisionsAndNobodyElseDoes() throws Exception {
        jdbc.sql("DELETE FROM agent_tenant_bindings WHERE tenant_id = 'it-tenant'").update();
        jdbc.sql("""
                INSERT INTO agent_tenant_bindings (tenant_id, agent_id, enabled, model_alias, pinned_version,
                  fallback_allowed, fallback_on_not_found, updated_at)
                VALUES ('it-tenant', 'triage', TRUE, 'agent-chat', NULL, FALSE, FALSE, ?)""")
                .params(OffsetDateTime.now()).update();
        jdbc.sql("INSERT INTO agent_tenant_binding_tools VALUES ('it-tenant','triage','git.read_file')").update();
        runScopes.register(new WorkflowRunScope("wf-it", "run-it", null));
        var runtime = jwt().jwt(j -> j.claim("azp", "openjcockpit-agent-runtime"));

        mockMvc.perform(get("/internal/agent-runtime/v1/tenants/it-tenant/agent-bindings/triage").with(runtime))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelAlias").value("agent-chat"))
                .andExpect(jsonPath("$.allowedTools[0]").value("git.read_file"));
        mockMvc.perform(get("/internal/agent-runtime/v1/tenants/other/agent-bindings/triage").with(runtime))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/internal/agent-runtime/v1/workflow-runs/run-it").with(runtime))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowId").value("wf-it"));
        mockMvc.perform(post("/internal/agent-runtime/v1/tool-invocation-decisions").with(runtime)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workflowId":"wf-it","workflowRunId":"run-it","tenantId":"it-tenant","agentId":"triage",
                                 "toolId":"git.read_file","principal":"alice","attributes":{"traceId":"t"}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));

        mockMvc.perform(get("/internal/agent-runtime/v1/workflow-runs/run-it"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/agent-runtime/v1/workflow-runs/run-it").with(jwt()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/internal/agent-runtime/v1/tenants/it-tenant/agent-bindings/triage")
                        .with(jwt().jwt(j -> j.claim("azp", "openjcockpit-a2a"))))
                .andExpect(status().isForbidden());
        runScopes.remove("run-it");
    }
}
