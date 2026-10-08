package nl.metafactory.agents.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.embabel.common.ai.model.ModelProvider;
import java.util.List;
import nl.metafactory.agents.approval.ApprovalDecisionAuditRepository;
import nl.metafactory.agents.approval.ApprovalDecisionService;
import nl.metafactory.agents.approval.ApprovalGateContextAssembler;
import nl.metafactory.agents.approval.ApprovalGateRegistry;
import nl.metafactory.agents.model.AgentDefinition;
import nl.metafactory.agents.orchestration.AgentOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves — with a real MockMvc call against the real actuator readiness health group endpoint —
 * that {@code management.endpoint.health.group.readiness.include: readinessState,db} genuinely
 * causes the readiness response to include database-availability information, not merely that
 * the YAML key exists.
 *
 * <p><b>Why the readiness properties are repeated here via {@code @SpringBootTest(properties=...)}
 * instead of relying on {@code src/main/resources/application.yml}, which already carries this
 * exact configuration:</b> {@code src/test/resources/application.yml} is present on the test
 * classpath ahead of {@code src/main/resources/application.yml} for Spring Boot's default
 * {@code classpath:/application.yml} config-data location, so it entirely shadows (not merges
 * with) the main resource for every test in this module — the same pre-existing, out-of-scope
 * classpath-shadowing behavior already named for the datasource config elsewhere in this
 * delivery. The test-side {@code application.yml} does not carry the
 * {@code management.endpoint.health.*} keys, so without repeating them here no test in this
 * module could ever observe the real readiness-group behavior. Both {@code application.yml}
 * files are out of scope for this work package, so the properties are supplied at the test level
 * instead, using values identical to the ones already present in
 * {@code src/main/resources/application.yml}. This was confirmed empirically: without these
 * inline properties, {@code GET /actuator/health/readiness} in this test context returns only
 * {@code {"status":"UP"}} (the hard-coded, component-free fallback
 * {@code AvailabilityProbesHealthEndpointGroup} Spring Boot uses when no matching custom
 * "readiness" group is configured); with them applied, the same request returns a body
 * containing a {@code db} component, proving the configuration genuinely works once loaded.
 *
 * <p>Unlike the plain {@code /actuator/health} path (permitted anonymously by
 * {@code SecurityConfig}'s exact-path matcher), {@code /actuator/health/readiness} is NOT in that
 * permit-all list, so this request authenticates with a JWT, matching this module's established
 * {@code SecurityConfigTest} pattern for authenticated MockMvc calls.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK, properties = {
        "management.endpoint.health.show-details=always",
        "management.endpoint.health.show-components=always",
        "management.endpoint.health.probes.enabled=true",
        "management.endpoint.health.group.readiness.include=readinessState,db"
})
@AutoConfigureMockMvc
class ReadinessHealthGroupTest {

    @MockitoBean ModelProvider modelProvider;

    @Autowired MockMvc mockMvc;
    @MockitoBean AgentOrchestrator orchestrator;
    @MockitoBean ApprovalGateRegistry approvalGateRegistry;
    @MockitoBean ApprovalGateContextAssembler approvalGateContextAssembler;
    @MockitoBean ApprovalDecisionService approvalDecisionService;
    @MockitoBean ApprovalDecisionAuditRepository approvalDecisionAuditRepository;

    @BeforeEach
    void setUp() {
        when(orchestrator.availableAgents()).thenReturn(List.of(
                new AgentDefinition("req", "Requirement Agent", "Extract requirements",
                        "specification", List.of(), List.of(), 0, "SpecContent", "RequirementAnalysis")
        ));
    }

    @Test
    void readinessGroupIncludesDatabaseAvailabilityAndIsUp() throws Exception {
        String body = mockMvc.perform(get("/actuator/health/readiness").with(jwt()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("\"status\":\"UP\"");
        assertThat(body).contains("\"db\"");
        assertThat(body).contains("\"components\"");
    }
}
