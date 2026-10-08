package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.model.ExternalSkillDto;
import nl.metafactory.aicontrol.model.SkillCatalogDto;
import nl.metafactory.aicontrol.service.SkillCatalogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deliberately does <strong>not</strong> exclude Spring Security auto-configuration (unlike
 * {@code SkillsMarketplaceControllerTest}) — AC-19/AC-20 require the real filter chain to reject
 * before the controller is ever reached, proven by {@code verifyNoInteractions(service)}. Mirrors
 * the established {@code AgenticWorkflowControllerTest} pattern.
 */
@WebMvcTest(controllers = SkillCatalogController.class)
class SkillCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SkillCatalogService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatedRequestReturns200WithTheDocumentedShape() throws Exception {
        when(service.getCatalog()).thenReturn(new SkillCatalogDto(List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/skill-catalog").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.localSkills").isArray())
                .andExpect(jsonPath("$.externalSkills").isArray())
                .andExpect(jsonPath("$.sources").isArray());
    }

    // AC-19: no bearer token at all.
    @Test
    void unauthenticatedRequestIsRejectedWith401BeforeTouchingTheService() throws Exception {
        mockMvc.perform(get("/api/skill-catalog"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    // AC-20: an expired-or-wrong-issuer token, simulated via a decoder that rejects it.
    @Test
    void invalidTokenIsRejectedWith401BeforeTouchingTheService() throws Exception {
        when(jwtDecoder.decode("wrong-issuer-token")).thenThrow(new BadJwtException("Invalid issuer"));

        mockMvc.perform(get("/api/skill-catalog").header("Authorization", "Bearer wrong-issuer-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    // AC-21: an authenticated realm user with no roles assigned (the only kind that exists) still
    // sees external skills — the accepted "authenticated-only" posture.
    @Test
    void authenticatedUserWithNoRolesStillSeesExternalSkills() throws Exception {
        var external = new ExternalSkillDto(UUID.randomUUID(), "Acme Skills", "code-review", "desc");
        when(service.getCatalog()).thenReturn(new SkillCatalogDto(List.of(), List.of(external), List.of()));

        mockMvc.perform(get("/api/skill-catalog").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalSkills[0].name").value("code-review"))
                .andExpect(jsonPath("$.externalSkills[0].marketplaceName").value("Acme Skills"));
    }
}
