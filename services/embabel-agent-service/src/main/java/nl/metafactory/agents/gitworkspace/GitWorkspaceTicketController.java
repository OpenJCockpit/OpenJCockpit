package nl.metafactory.agents.gitworkspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal, service-to-service: ai-control's service identity issues a single-use ticket that binds one
 * workflow start to a git workspace it prepared. The ticket is for one workflow and one subject: the
 * user who starts the run (with their own token, so policy and run attribution stay theirs), or — when
 * {@code subject} is omitted — ai-control itself (git-workspace jobs). Every other caller gets 403.
 */
@RestController
public class GitWorkspaceTicketController {

    private static final Logger log = LoggerFactory.getLogger(GitWorkspaceTicketController.class);

    public record TicketRequest(String workspaceKey, String repositoryUrl, String branch, String workflowId,
                                String subject) {
    }

    private final GitWorkspaceTickets tickets;
    private final GitWorkspaceProperties properties;

    public GitWorkspaceTicketController(GitWorkspaceTickets tickets, GitWorkspaceProperties properties) {
        this.tickets = tickets;
        this.properties = properties;
    }

    @PostMapping("/internal/git-workspace-tickets")
    public ResponseEntity<GitWorkspaceTickets.Issued> issue(@RequestBody TicketRequest request,
                                                            @AuthenticationPrincipal Jwt jwt) {
        String caller = jwt == null ? null : jwt.getClaimAsString("azp");
        if (caller == null || !caller.equals(properties.getTrustedClientId())) {
            log.warn("git.workspace.ticket.denied reason=caller-not-allowed caller={}", caller);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String subject = request.subject() == null || request.subject().isBlank() ? jwt.getSubject() : request.subject();
        try {
            var workspace = new GitWorkspaceBinding(request.workspaceKey(), request.repositoryUrl(), request.branch());
            var issued = tickets.issue(workspace, request.workflowId(), subject);
            log.info("git.workspace.ticket.issued workspace={} workflowId={}", workspace.workspaceKey(), request.workflowId());
            return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(issued);
        } catch (IllegalArgumentException | NullPointerException e) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
    }
}
