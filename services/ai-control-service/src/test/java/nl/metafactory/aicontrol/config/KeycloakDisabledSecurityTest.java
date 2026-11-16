package nl.metafactory.aicontrol.config;

import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.client.AgentRunDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK, properties = "ENABLE_KEYCLOAK=false")
@AutoConfigureMockMvc
class KeycloakDisabledSecurityTest {

    @Autowired MockMvc mockMvc;
    @Autowired ApplicationContext context;
    @MockitoBean EmbabelAgentClient embabelAgentClient;

    @Test
    void withoutKeycloakEveryRequestIsPermittedAsTheLocalIdentity() throws Exception {
        assertThat(context.getBeanNamesForType(SecurityConfig.class)).isEmpty();
        when(embabelAgentClient.getLatestRun("run-1")).thenReturn(Optional.of(new AgentRunDto("run-1", "cust",
                "spec", "", "COMPLETED", Instant.now(), List.of(), List.of(), null, null, null, null)));

        mockMvc.perform(get("/api/agent-runs/run-1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value("run-1"));
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void productionRefusesToRunWithoutKeycloak() {
        var production = new MockEnvironment();
        production.setActiveProfiles("prod");
        assertThatThrownBy(() -> new KeycloakDisabledSecurityConfig(production))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(SecurityConfig.KEYCLOAK_ENABLED);

        var local = new MockEnvironment();
        local.setActiveProfiles("local");
        assertThatCode(() -> new KeycloakDisabledSecurityConfig(local)).doesNotThrowAnyException();
    }
}
