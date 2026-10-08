package nl.metafactory.agents.api;

import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.agents.policy.OpenPolicyAgentClient;
import nl.metafactory.agents.policy.config.OpaProperties;
import nl.metafactory.agents.policy.model.FailMode;
import nl.metafactory.agents.policy.model.OpaConfigRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = OpaConfigController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class}
)
class OpaConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private OpaProperties properties;

    @MockitoBean
    private OpenPolicyAgentClient client;

    @BeforeEach
    void setUp() {
        when(properties.isEnabled()).thenReturn(false);
        when(properties.getBaseUrl()).thenReturn("http://localhost:8181");
        when(properties.getPolicyPath()).thenReturn("/v1/data/openjcockpit/workflow/decision");
        when(properties.getHealthPath()).thenReturn("/health");
        when(properties.getTimeoutSeconds()).thenReturn(3);
        when(properties.getFailMode()).thenReturn(FailMode.FAIL_CLOSED);
        when(properties.isDecisionLoggingEnabled()).thenReturn(true);
        when(properties.isDecisionLogExportEnabled()).thenReturn(true);
        when(properties.getEnvironment()).thenReturn("staging");
        when(properties.getCustomerLabel()).thenReturn("noordzee");
        when(properties.getProjectLabel()).thenReturn("onboarding");
        when(properties.getAuthToken()).thenReturn(null);
    }

    @Test
    void getReturnsCurrentConfigWithoutRawToken() throws Exception {
        mockMvc.perform(get("/api/opa-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.baseUrl").value("http://localhost:8181"))
                .andExpect(jsonPath("$.hasAuthToken").value(false));
    }

    @Test
    void getReflectsAuthTokenPresence() throws Exception {
        when(properties.getAuthToken()).thenReturn("secret-token");

        mockMvc.perform(get("/api/opa-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAuthToken").value(true));
    }

    @Test
    void updateAppliesAllProvidedFields() throws Exception {
        var request = new OpaConfigRequest(true, "http://opa-policy-service:8181", "/v1/data/custom/decision",
                "/health/ready", 5, "FAIL_OPEN", false, false, "production", "acme", "billing", "new-token");

        mockMvc.perform(put("/api/opa-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        var inOrder = org.mockito.Mockito.inOrder(properties);
        inOrder.verify(properties).setEnabled(true);
        inOrder.verify(properties).setBaseUrl("http://opa-policy-service:8181");
        inOrder.verify(properties).setPolicyPath("/v1/data/custom/decision");
        inOrder.verify(properties).setHealthPath("/health/ready");
        inOrder.verify(properties).setTimeoutSeconds(5);
        inOrder.verify(properties).setFailMode(FailMode.FAIL_OPEN);
        inOrder.verify(properties).setDecisionLoggingEnabled(false);
        inOrder.verify(properties).setDecisionLogExportEnabled(false);
        inOrder.verify(properties).setEnvironment("production");
        inOrder.verify(properties).setCustomerLabel("acme");
        inOrder.verify(properties).setProjectLabel("billing");
        inOrder.verify(properties).setAuthToken("new-token");
    }

    @Test
    void updateWithOnlyEnabledLeavesOtherFieldsUnchanged() throws Exception {
        var request = new OpaConfigRequest(true, null, null, null, null, null, null, null, null, null, null, null);

        mockMvc.perform(put("/api/opa-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(properties).setEnabled(true);
        org.mockito.Mockito.verify(properties, org.mockito.Mockito.never()).setBaseUrl(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(properties, org.mockito.Mockito.never()).setPolicyPath(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(properties, org.mockito.Mockito.never()).setHealthPath(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(properties, org.mockito.Mockito.never()).setTimeoutSeconds(org.mockito.ArgumentMatchers.anyInt());
        org.mockito.Mockito.verify(properties, org.mockito.Mockito.never()).setFailMode(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(properties, org.mockito.Mockito.never()).setDecisionLoggingEnabled(org.mockito.ArgumentMatchers.anyBoolean());
        org.mockito.Mockito.verify(properties, org.mockito.Mockito.never()).setDecisionLogExportEnabled(org.mockito.ArgumentMatchers.anyBoolean());
        org.mockito.Mockito.verify(properties, org.mockito.Mockito.never()).setEnvironment(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(properties, org.mockito.Mockito.never()).setCustomerLabel(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(properties, org.mockito.Mockito.never()).setProjectLabel(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(properties, org.mockito.Mockito.never()).setAuthToken(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void healthReturnsClientReachability() throws Exception {
        when(client.isReachable()).thenReturn(true);

        mockMvc.perform(get("/api/opa-config/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reachable").value(true));
    }
}
