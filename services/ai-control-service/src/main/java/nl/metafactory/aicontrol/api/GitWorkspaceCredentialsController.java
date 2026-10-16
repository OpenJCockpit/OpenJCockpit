package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.config.GitWorkspaceProperties;
import nl.metafactory.aicontrol.service.GitWorkspaceCredentialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Internal, service-to-service: the git MCP server pulls the git credentials of an active job
 * workspace. Not part of the public API contract and not proxied by the dashboard ({@code /api/} only).
 * Only the git MCP server's own Keycloak client ({@code azp}) is answered; every other caller,
 * including any user token, gets 403.
 */
@RestController
public class GitWorkspaceCredentialsController {

    private static final Logger log = LoggerFactory.getLogger(GitWorkspaceCredentialsController.class);

    private final GitWorkspaceCredentialService credentials;
    private final GitWorkspaceProperties properties;

    public GitWorkspaceCredentialsController(GitWorkspaceCredentialService credentials, GitWorkspaceProperties properties) {
        this.credentials = credentials;
        this.properties = properties;
    }

    @GetMapping("/internal/git-workspaces/{workspaceKey}/credentials")
    public ResponseEntity<Map<String, String>> credentials(@PathVariable String workspaceKey,
                                                           @RequestParam String repositoryUrl,
                                                           @AuthenticationPrincipal Jwt jwt) {
        String caller = jwt == null ? null : jwt.getClaimAsString("azp");
        if (caller == null || !caller.equals(properties.getCredentialClientId())) {
            log.warn("git.workspace.credentials.denied workspace={} reason=caller-not-allowed", workspaceKey);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return credentials.credentialsFor(workspaceKey, repositoryUrl)
                .map(c -> {
                    var body = new HashMap<String, String>();
                    body.put("username", c.username());
                    body.put("token", c.token());
                    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body((Map<String, String>) body);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}
