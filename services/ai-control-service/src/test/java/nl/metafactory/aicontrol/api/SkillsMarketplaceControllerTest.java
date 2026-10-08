package nl.metafactory.aicontrol.api;

import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.aicontrol.model.SkillsMarketplaceConnectionDto;
import nl.metafactory.aicontrol.model.SkillsMarketplaceConnectionRequest;
import nl.metafactory.aicontrol.service.SkillsMarketplaceConnectionService;
import nl.metafactory.aicontrol.service.SkillsMarketplaceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = SkillsMarketplaceController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class}
)
class SkillsMarketplaceControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper objectMapper;
    @MockitoBean private SkillsMarketplaceConnectionService service;

    // This slice excludes the Spring Security auto-configuration entirely (the
    // ProjectIntegrationConfigControllerTest pattern), so there is no filter chain to propagate
    // SecurityMockMvcRequestPostProcessors' TestSecurityContextHolder into the real
    // SecurityContextHolder. Setting it directly on the test thread is the correct approach here
    // (MockMvc dispatches synchronously on the same thread) and is cleared after every test.
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private SkillsMarketplaceConnectionDto dto(UUID id, boolean hasApiKey) {
        var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return new SkillsMarketplaceConnectionDto(id, "Acme Skills", "https://marketplace.example.com/api",
                "desc", true, hasApiKey, "ricky", now, now);
    }

    private void authenticateAs(String username) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("preferred_username", username)
                .build();
        var auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ── list ─────────────────────────────────────────────────────────────────

    @Test
    void listReturns200WithNoApiKeyFieldInResponse() throws Exception {
        var id = UUID.randomUUID();
        when(service.list()).thenReturn(List.of(dto(id, true)));

        var result = mockMvc.perform(get("/api/skills-marketplaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hasApiKey").value(true))
                .andExpect(jsonPath("$[0].apiKey").doesNotExist())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("\"apiKey\"");
    }

    // ── create ───────────────────────────────────────────────────────────────

    @Test
    void createReturns201() throws Exception {
        var id = UUID.randomUUID();
        when(service.create(any(), eq("ricky"))).thenReturn(dto(id, true));

        var req = new SkillsMarketplaceConnectionRequest("Acme Skills", "https://marketplace.example.com/api",
                "secret-key-value", "desc", true);

        authenticateAs("ricky");
        mockMvc.perform(post("/api/skills-marketplaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Acme Skills"))
                .andExpect(jsonPath("$.hasApiKey").value(true));
    }

    @Test
    void createWithoutAuthenticationUsesUnknownActor() throws Exception {
        var id = UUID.randomUUID();
        when(service.create(any(), eq("unknown"))).thenReturn(dto(id, true));

        var req = new SkillsMarketplaceConnectionRequest("Acme Skills", "https://marketplace.example.com/api",
                "secret-key-value", "desc", true);

        mockMvc.perform(post("/api/skills-marketplaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        verify(service).create(any(), eq("unknown"));
    }

    @Test
    void createRejectsBlankNameWith400AndNonEmptyMessage() throws Exception {
        var req = new SkillsMarketplaceConnectionRequest("", "https://marketplace.example.com/api",
                "secret-key-value", null, true);

        mockMvc.perform(post("/api/skills-marketplaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void createRejectsFtpUrlWith400MentioningMarketplaceUrl() throws Exception {
        var req = new SkillsMarketplaceConnectionRequest("Acme Skills", "ftp://marketplace.example.com",
                "secret-key-value", null, true);

        mockMvc.perform(post("/api/skills-marketplaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("marketplaceUrl")));
    }

    @Test
    void createRejectsNotAUrlWith400MentioningMarketplaceUrl() throws Exception {
        var req = new SkillsMarketplaceConnectionRequest("Acme Skills", "not-a-url",
                "secret-key-value", null, true);

        mockMvc.perform(post("/api/skills-marketplaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("marketplaceUrl")));
    }

    @Test
    void createRejectsOverLengthNameWith400() throws Exception {
        var req = new SkillsMarketplaceConnectionRequest("a".repeat(101), "https://marketplace.example.com/api",
                "secret-key-value", null, true);

        mockMvc.perform(post("/api/skills-marketplaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void createRejectsOverLengthUrlWith400() throws Exception {
        var req = new SkillsMarketplaceConnectionRequest("Acme Skills", "https://marketplace.example.com/" + "a".repeat(500),
                "secret-key-value", null, true);

        mockMvc.perform(post("/api/skills-marketplaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void createReturns400WhenApiKeyRequired() throws Exception {
        when(service.create(any(), anyString()))
                .thenThrow(new SkillsMarketplaceException(SkillsMarketplaceException.Code.API_KEY_REQUIRED, "apiKey is required"));

        var req = new SkillsMarketplaceConnectionRequest("Acme Skills", "https://marketplace.example.com/api",
                null, null, true);

        mockMvc.perform(post("/api/skills-marketplaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("API_KEY_REQUIRED"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void createReturns409OnNameConflict() throws Exception {
        when(service.create(any(), anyString()))
                .thenThrow(new SkillsMarketplaceException(SkillsMarketplaceException.Code.NAME_CONFLICT, "already exists"));

        var req = new SkillsMarketplaceConnectionRequest("Acme Skills", "https://marketplace.example.com/api",
                "secret-key-value", null, true);

        mockMvc.perform(post("/api/skills-marketplaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NAME_CONFLICT"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // ── update ───────────────────────────────────────────────────────────────

    @Test
    void updateReturns200() throws Exception {
        var id = UUID.randomUUID();
        when(service.update(eq(id), any(), eq("ricky"))).thenReturn(dto(id, true));

        var req = new SkillsMarketplaceConnectionRequest("Acme Skills", "https://marketplace.example.com/api-v2",
                null, null, true);

        authenticateAs("ricky");
        mockMvc.perform(put("/api/skills-marketplaces/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasApiKey").value(true));
    }

    @Test
    void updateReturns404WhenNotFound() throws Exception {
        var id = UUID.randomUUID();
        when(service.update(eq(id), any(), anyString()))
                .thenThrow(new SkillsMarketplaceException(SkillsMarketplaceException.Code.NOT_FOUND, "not found"));

        var req = new SkillsMarketplaceConnectionRequest("Acme Skills", "https://marketplace.example.com/api",
                null, null, true);

        mockMvc.perform(put("/api/skills-marketplaces/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void updateReturns409OnNameConflict() throws Exception {
        var id = UUID.randomUUID();
        when(service.update(eq(id), any(), anyString()))
                .thenThrow(new SkillsMarketplaceException(SkillsMarketplaceException.Code.NAME_CONFLICT, "already exists"));

        var req = new SkillsMarketplaceConnectionRequest("Beta", "https://marketplace.example.com/api",
                null, null, true);

        mockMvc.perform(put("/api/skills-marketplaces/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NAME_CONFLICT"));
    }

    @Test
    void updateRejectsBlankNameWith400() throws Exception {
        var id = UUID.randomUUID();
        var req = new SkillsMarketplaceConnectionRequest("", "https://marketplace.example.com/api",
                null, null, true);

        mockMvc.perform(put("/api/skills-marketplaces/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // ── delete ───────────────────────────────────────────────────────────────

    @Test
    void deleteReturns204() throws Exception {
        var id = UUID.randomUUID();

        authenticateAs("ricky");
        mockMvc.perform(delete("/api/skills-marketplaces/{id}", id))
                .andExpect(status().isNoContent());

        verify(service).delete(id, "ricky");
    }

    @Test
    void deleteReturns404WhenNotFound() throws Exception {
        var id = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new SkillsMarketplaceException(SkillsMarketplaceException.Code.NOT_FOUND, "not found"))
                .when(service).delete(eq(id), anyString());

        mockMvc.perform(delete("/api/skills-marketplaces/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }
}
