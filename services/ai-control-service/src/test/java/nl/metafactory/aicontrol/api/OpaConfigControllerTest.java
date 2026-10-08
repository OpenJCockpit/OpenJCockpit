package nl.metafactory.aicontrol.api;

import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.client.OpaConfigDto;
import nl.metafactory.aicontrol.client.OpaConfigRequestDto;
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

import static org.mockito.ArgumentMatchers.any;
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

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper objectMapper;
    @MockitoBean EmbabelAgentClient client;

    private OpaConfigDto config() {
        return new OpaConfigDto(true, "http://localhost:8181", "/v1/data/openjcockpit/workflow/decision",
                "/health", 3, "FAIL_CLOSED", true, true, "staging", "noordzee", "onboarding", false);
    }

    @Test
    void getReturnsConfigFromClient() throws Exception {
        when(client.getOpaConfig()).thenReturn(config());

        mockMvc.perform(get("/api/opa-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseUrl").value("http://localhost:8181"));
    }

    @Test
    void updateSavesConfigViaClient() throws Exception {
        when(client.saveOpaConfig(any())).thenReturn(config());
        var request = new OpaConfigRequestDto(true, "http://localhost:8181", null, null, null, null,
                null, null, null, null, null, null);

        mockMvc.perform(put("/api/opa-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void healthReturnsClientReachability() throws Exception {
        when(client.checkOpaHealth()).thenReturn(true);

        mockMvc.perform(get("/api/opa-config/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reachable").value(true));
    }
}
