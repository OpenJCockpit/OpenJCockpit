package nl.metafactory.aicontrol.api;

import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.aicontrol.model.*;
import nl.metafactory.aicontrol.service.ModelRoutingService;
import nl.metafactory.aicontrol.service.WorkspaceService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = WorkspaceController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class}
)
class WorkspaceControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper objectMapper;
    @MockitoBean WorkspaceService workspaceService;
    @MockitoBean ModelRoutingService modelRoutingService;

    @Test
    void getWorkspaceReturnsOk() throws Exception {
        var workspace = new Workspace(
            new CustomerSummary("cust", "Cust NV", "IT", "Prod", "now", "Internal", "eu-west-1"),
            "spec.md", List.of(), List.of(), List.of(), List.of(),
            new EvidenceDetails("v1", "run-1", "flow", "now"),
            List.of(), List.of(),
            new FooterStatus("Prod", "eu-west-1", "Internal", "1.0", "1.0")
        );
        when(workspaceService.loadWorkspace("cust")).thenReturn(workspace);

        mockMvc.perform(get("/api/workspaces/cust"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.customer.id").value("cust"));
    }

    @Test
    void postModelRoutingDecisionReturnsDecision() throws Exception {
        var decision = new ModelRouteDecision("CLOUD", "cloud-fast", true, "reason", List.of());
        when(modelRoutingService.decide(any())).thenReturn(decision);

        var request = new ModelRouteRequest("c", "s", DataClassification.L0_PUBLIC, "gen");
        mockMvc.perform(post("/api/model-routing/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.route").value("CLOUD"));
    }

    @Test
    void postModelRoutingValidationFailsOnBlankCustomerId() throws Exception {
        var request = new ModelRouteRequest("", "s", DataClassification.L0_PUBLIC, "gen");
        mockMvc.perform(post("/api/model-routing/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isBadRequest());
    }
}
