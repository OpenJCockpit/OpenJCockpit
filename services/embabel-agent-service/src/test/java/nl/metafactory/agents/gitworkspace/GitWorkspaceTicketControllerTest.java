package nl.metafactory.agents.gitworkspace;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class GitWorkspaceTicketControllerTest {

    private final GitWorkspaceTickets tickets = new GitWorkspaceTickets(new GitWorkspaceProperties());
    private final GitWorkspaceTicketController controller = new GitWorkspaceTicketController(tickets, new GitWorkspaceProperties());

    private static Jwt token(String azp, String sub) {
        return Jwt.withTokenValue("t").header("alg", "RS256").subject(sub).claim("azp", azp)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }

    private static GitWorkspaceTicketController.TicketRequest request(String subject) {
        return new GitWorkspaceTicketController.TicketRequest("job-1", "https://r/repo.git", "openjcockpit/wf-1/abc",
                "wf-1", subject);
    }

    @Test
    void aiControlGetsATicketForTheNamedUser() {
        var response = controller.issue(request("alice-sub"), token("openjcockpit-ai-control", "svc-sub"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(tickets.redeem(response.getBody().ticket(), "wf-1", "alice-sub")).isPresent();
    }

    @Test
    void withoutSubjectTheTicketIsForAiControlItself() {
        var response = controller.issue(request(null), token("openjcockpit-ai-control", "svc-sub"));

        assertThat(tickets.redeem(response.getBody().ticket(), "wf-1", "svc-sub")).isPresent();
    }

    @Test
    void anyOtherCallerIsForbidden() {
        assertThat(controller.issue(request("alice-sub"), token("openjcockpit-dashboard", "alice-sub")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(controller.issue(request("alice-sub"), null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void anInvalidWorkspaceIsABadRequest() {
        var bad = new GitWorkspaceTicketController.TicketRequest("../x", "https://r", "b", "wf-1", "alice-sub");

        assertThat(controller.issue(bad, token("openjcockpit-ai-control", "svc")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void tooManyOutstandingTicketsIsTooManyRequests() {
        for (int i = 0; i < GitWorkspaceTickets.MAX_OUTSTANDING; i++) {
            controller.issue(request("alice-sub"), token("openjcockpit-ai-control", "svc-sub"));
        }

        assertThat(controller.issue(request("alice-sub"), token("openjcockpit-ai-control", "svc-sub")).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
