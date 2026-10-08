package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.model.SpecFile;
import nl.metafactory.aicontrol.service.SpecRepositoryClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = SpecController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class}
)
class SpecControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean SpecRepositoryClient specRepositoryClient;

    @Test
    void getSpecsReturnsListForKnownCustomer() throws Exception {
        when(specRepositoryClient.listSpecs("noordzee-logistics")).thenReturn(List.of(
                new SpecFile("pricing-rules", "pricing-rules.spec.md", "", "14 jun 10:30", "Active", true, "# content", "https://github.com/noordzee-logistics/pricing-engine")
        ));

        mockMvc.perform(get("/api/specs/noordzee-logistics"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].id").value("pricing-rules"))
               .andExpect(jsonPath("$[0].fileName").value("pricing-rules.spec.md"));
    }

    @Test
    void getSpecsReturnsEmptyArrayForUnknownCustomer() throws Exception {
        when(specRepositoryClient.listSpecs("unknown")).thenReturn(List.of());

        mockMvc.perform(get("/api/specs/unknown"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$").isArray())
               .andExpect(jsonPath("$").isEmpty());
    }
}
