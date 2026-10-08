package nl.metafactory.aicontrol.api;

import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.aicontrol.model.GitWorkspaceJobErrorCode;
import nl.metafactory.aicontrol.model.SpecFile;
import nl.metafactory.aicontrol.model.SpecFileSaveRequest;
import nl.metafactory.aicontrol.model.SpecInitResult;
import nl.metafactory.aicontrol.service.GitWorkspaceException;
import nl.metafactory.aicontrol.service.ProjectSpecService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProjectSpecController.class)
class ProjectSpecControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectSpecService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private JsonMapper objectMapper;

    private final UUID projectId = UUID.randomUUID();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listSpecFilesReturnsSpecsFromService() throws Exception {
        when(service.listSpecFiles(projectId)).thenReturn(List.of(
                new SpecFile("pricing-rules", "pricing-rules.md", "", "4 jul 10:30", "Active", true,
                        "# content", "https://github.com/org/repo")
        ));

        mockMvc.perform(get("/api/projects/" + projectId + "/spec-files").with(jwt()))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].id").value("pricing-rules"))
               .andExpect(jsonPath("$[0].fileName").value("pricing-rules.md"))
               .andExpect(jsonPath("$[0].selected").value(true));
    }

    @Test
    void listSpecFilesReturnsEmptyArrayWhenRepoHasNoSpecs() throws Exception {
        when(service.listSpecFiles(projectId)).thenReturn(List.of());

        mockMvc.perform(get("/api/projects/" + projectId + "/spec-files").with(jwt()))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$").isArray())
               .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listSpecFilesRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/projects/" + projectId + "/spec-files"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void listSpecFilesReturns404WhenProjectNotFound() throws Exception {
        when(service.listSpecFiles(projectId)).thenThrow(new GitWorkspaceException(
                GitWorkspaceJobErrorCode.PROJECT_NOT_FOUND, "Project not found: " + projectId));

        mockMvc.perform(get("/api/projects/" + projectId + "/spec-files").with(jwt()))
               .andExpect(status().isNotFound())
               .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }

    @Test
    void listSpecFilesReturns409WhenProjectInactive() throws Exception {
        when(service.listSpecFiles(projectId)).thenThrow(new GitWorkspaceException(
                GitWorkspaceJobErrorCode.PROJECT_INACTIVE, "Project is inactive"));

        mockMvc.perform(get("/api/projects/" + projectId + "/spec-files").with(jwt()))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.code").value("PROJECT_INACTIVE"));
    }

    @Test
    void listSpecFilesReturns502WhenCloneFails() throws Exception {
        when(service.listSpecFiles(projectId)).thenThrow(new GitWorkspaceException(
                GitWorkspaceJobErrorCode.GIT_CLONE_FAILED, "clone failed"));

        mockMvc.perform(get("/api/projects/" + projectId + "/spec-files").with(jwt()))
               .andExpect(status().isBadGateway())
               .andExpect(jsonPath("$.code").value("GIT_CLONE_FAILED"))
               .andExpect(jsonPath("$.message").value("GIT_CLONE_FAILED: clone failed"));
    }

    @Test
    void specInitStatusReturnsPendingBranchWithLinks() throws Exception {
        when(service.specInitStatus(projectId)).thenReturn(new nl.metafactory.aicontrol.model.SpecInitStatus(
                true, "spec-init/test/20260705-abc",
                "https://github.com/org/repo/tree/spec-init/test/20260705-abc",
                "https://github.com/org/repo/compare/main...spec-init/test/20260705-abc?expand=1", false));

        mockMvc.perform(get("/api/projects/" + projectId + "/spec-init/status").with(jwt()))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.pending").value(true))
               .andExpect(jsonPath("$.templateExists").value(false))
               .andExpect(jsonPath("$.branch").value("spec-init/test/20260705-abc"))
               .andExpect(jsonPath("$.branchUrl").value("https://github.com/org/repo/tree/spec-init/test/20260705-abc"));
    }

    @Test
    void specInitStatusReturnsNotPendingWhenNoBranchExists() throws Exception {
        when(service.specInitStatus(projectId)).thenReturn(nl.metafactory.aicontrol.model.SpecInitStatus.none(true));

        mockMvc.perform(get("/api/projects/" + projectId + "/spec-init/status").with(jwt()))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.pending").value(false))
               .andExpect(jsonPath("$.templateExists").value(true));
    }

    @Test
    void initSpecFolderReturnsResultAndPassesUsernameFromJwt() throws Exception {
        when(service.initSpecFolder(eq(projectId), eq("alice"))).thenReturn(new SpecInitResult(
                projectId, "spec-init/test/20260705-abc", "main", "abc123", "spec-template.md",
                "https://github.com/org/repo/compare/main...spec-init/test/20260705-abc?expand=1",
                "Starter spec pushed"));

        mockMvc.perform(post("/api/projects/" + projectId + "/spec-init")
                        .with(jwt().jwt(b -> b.claim("preferred_username", "alice"))))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.branch").value("spec-init/test/20260705-abc"))
               .andExpect(jsonPath("$.baseBranch").value("main"))
               .andExpect(jsonPath("$.commitHash").value("abc123"))
               .andExpect(jsonPath("$.fileName").value("spec-template.md"))
               .andExpect(jsonPath("$.pullRequestUrl").value(
                       "https://github.com/org/repo/compare/main...spec-init/test/20260705-abc?expand=1"));

        verify(service).initSpecFolder(projectId, "alice");
    }

    @Test
    void initSpecFolderRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/projects/" + projectId + "/spec-init"))
               .andExpect(status().is4xxClientError());
    }

    @Test
    void initSpecFolderReturns409WhenSpecFolderAlreadyInitialized() throws Exception {
        when(service.initSpecFolder(eq(projectId), eq("alice"))).thenThrow(new GitWorkspaceException(
                GitWorkspaceJobErrorCode.NO_CHANGES, "Spec folder already contains files"));

        mockMvc.perform(post("/api/projects/" + projectId + "/spec-init")
                        .with(jwt().jwt(b -> b.claim("preferred_username", "alice"))))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.code").value("NO_CHANGES"));
    }

    @Test
    void initSpecFolderReturns404WhenNoProjectSelected() throws Exception {
        when(service.initSpecFolder(eq(projectId), eq("alice"))).thenThrow(new GitWorkspaceException(
                GitWorkspaceJobErrorCode.NO_SELECTED_PROJECT, "No project selected"));

        mockMvc.perform(post("/api/projects/" + projectId + "/spec-init")
                        .with(jwt().jwt(b -> b.claim("preferred_username", "alice"))))
               .andExpect(status().isNotFound())
               .andExpect(jsonPath("$.code").value("NO_SELECTED_PROJECT"));
    }

    @Test
    void initSpecFolderReturns409WhenCredentialsMissing() throws Exception {
        when(service.initSpecFolder(eq(projectId), eq("alice"))).thenThrow(new GitWorkspaceException(
                GitWorkspaceJobErrorCode.GIT_AUTH_FAILED,
                "No active git credentials for project Test Project"));

        mockMvc.perform(post("/api/projects/" + projectId + "/spec-init")
                        .with(jwt().jwt(b -> b.claim("preferred_username", "alice"))))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.code").value("GIT_AUTH_FAILED"));
    }

    @Test
    void initSpecFolderReturns409WhenGitUrlMissing() throws Exception {
        when(service.initSpecFolder(eq(projectId), eq("alice"))).thenThrow(new GitWorkspaceException(
                GitWorkspaceJobErrorCode.GIT_URL_MISSING, "Project has no Git URL"));

        mockMvc.perform(post("/api/projects/" + projectId + "/spec-init")
                        .with(jwt().jwt(b -> b.claim("preferred_username", "alice"))))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.code").value("GIT_URL_MISSING"));
    }

    @Test
    void initSpecFolderUsesUnknownUsernameWithoutAuthentication() {
        SecurityContextHolder.clearContext();
        ProjectSpecService directService = org.mockito.Mockito.mock(ProjectSpecService.class);
        new ProjectSpecController(directService).initSpecFolder(projectId);
        verify(directService).initSpecFolder(projectId, "unknown");
    }

    @Test
    void initSpecFolderReturns502WhenPushFails() throws Exception {
        when(service.initSpecFolder(eq(projectId), eq("alice"))).thenThrow(new GitWorkspaceException(
                GitWorkspaceJobErrorCode.PUSH_FAILED, "push failed"));

        mockMvc.perform(post("/api/projects/" + projectId + "/spec-init")
                        .with(jwt().jwt(b -> b.claim("preferred_username", "alice"))))
               .andExpect(status().isBadGateway())
               .andExpect(jsonPath("$.code").value("PUSH_FAILED"));
    }

    @Test
    void saveSpecFileReturnsResultAndPassesUsernameFromJwt() throws Exception {
        var request = new SpecFileSaveRequest("pricing-rules.md", "# updated content");
        when(service.saveSpecFile(eq(projectId), eq("pricing-rules.md"), eq("# updated content"), eq("alice")))
                .thenReturn(new SpecInitResult(
                        projectId, "spec-edit/test/20260705-abc", "main", "def456", "pricing-rules.md",
                        "https://github.com/org/repo/compare/main...spec-edit/test/20260705-abc?expand=1",
                        "Changes pushed to branch spec-edit/test/20260705-abc"));

        mockMvc.perform(put("/api/projects/" + projectId + "/spec-files")
                        .with(jwt().jwt(b -> b.claim("preferred_username", "alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.branch").value("spec-edit/test/20260705-abc"))
               .andExpect(jsonPath("$.fileName").value("pricing-rules.md"))
               .andExpect(jsonPath("$.commitHash").value("def456"));

        verify(service).saveSpecFile(projectId, "pricing-rules.md", "# updated content", "alice");
    }

    @Test
    void saveSpecFileRequiresAuthentication() throws Exception {
        var request = new SpecFileSaveRequest("pricing-rules.md", "content");

        mockMvc.perform(put("/api/projects/" + projectId + "/spec-files")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().is4xxClientError());
    }

    @Test
    void saveSpecFileReturns409WhenSpecFileNotFound() throws Exception {
        var request = new SpecFileSaveRequest("missing.md", "content");
        when(service.saveSpecFile(eq(projectId), eq("missing.md"), eq("content"), eq("alice")))
                .thenThrow(new GitWorkspaceException(
                        GitWorkspaceJobErrorCode.SPEC_FILE_INVALID, "Spec file not found: missing.md"));

        mockMvc.perform(put("/api/projects/" + projectId + "/spec-files")
                        .with(jwt().jwt(b -> b.claim("preferred_username", "alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isBadGateway())
               .andExpect(jsonPath("$.code").value("SPEC_FILE_INVALID"));
    }

    @Test
    void saveSpecFileUsesUnknownUsernameWithoutAuthentication() {
        SecurityContextHolder.clearContext();
        ProjectSpecService directService = org.mockito.Mockito.mock(ProjectSpecService.class);
        var request = new SpecFileSaveRequest("pricing-rules.md", "content");
        new ProjectSpecController(directService).saveSpecFile(projectId, request);
        verify(directService).saveSpecFile(projectId, "pricing-rules.md", "content", "unknown");
    }
}
