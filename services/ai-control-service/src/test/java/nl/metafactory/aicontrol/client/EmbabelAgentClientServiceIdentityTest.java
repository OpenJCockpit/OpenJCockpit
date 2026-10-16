package nl.metafactory.aicontrol.client;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Calls ai-control makes as its own service identity (git-workspace jobs), plus the agent catalog. */
class EmbabelAgentClientServiceIdentityTest {

    private static final String AUTH = "Bearer svc";

    private MockWebServer server;
    private EmbabelAgentClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new EmbabelAgentClient(WebClient.builder().baseUrl(server.url("/").toString()).build());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private void respond(int status, String body) {
        server.enqueue(new MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body));
    }

    @Test
    void startsReadsAndStopsARunWithTheGivenServiceIdentity() throws Exception {
        respond(200, "{\"workflowId\":\"wf-1\",\"executionId\":\"run-1\",\"status\":\"STARTED\"}");
        assertThat(client.startWorkflowAs(AUTH, "wf-1", null).executionId()).isEqualTo("run-1");
        var start = server.takeRequest();
        assertThat(start.getPath()).isEqualTo("/api/workflows/wf-1/start");
        assertThat(start.getHeader("Authorization")).isEqualTo(AUTH);

        respond(200, "{\"runId\":\"run-1\",\"status\":\"RUNNING\"}");
        assertThat(client.getLatestRunAs(AUTH, "run-1")).get().extracting(AgentRunDto::status).isEqualTo("RUNNING");
        assertThat(server.takeRequest().getHeader("Authorization")).isEqualTo(AUTH);

        respond(200, "[{\"agentId\":\"local:qa\",\"position\":0,\"output\":\"done\"}]");
        assertThat(client.getAgentRunOutputsAs(AUTH, "run-1")).singleElement()
                .extracting(AgentRunOutputDto::output).isEqualTo("done");
        assertThat(server.takeRequest().getPath()).isEqualTo("/api/agent-runs/run-1/outputs");

        server.enqueue(new MockResponse().setResponseCode(204));
        client.stopAgentRunAs(AUTH, "run-1");
        var stop = server.takeRequest();
        assertThat(stop.getMethod() + " " + stop.getPath()).isEqualTo("DELETE /api/agent-runs/run-1");
        assertThat(stop.getHeader("Authorization")).isEqualTo(AUTH);
    }

    @Test
    void anUnknownWorkflowIsNotFoundAndAnUnreadableRunIsEmpty() {
        respond(404, "{}");
        assertThatThrownBy(() -> client.startWorkflowAs(AUTH, "wf-x", WorkflowStartInputDto.empty()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        respond(500, "{}");
        assertThat(client.getLatestRunAs(AUTH, "run-1")).isEmpty();
    }

    @Test
    void readsTheOutputsOfARun() throws Exception {
        respond(200, "[{\"agentId\":\"local:qa\",\"position\":0,\"output\":\"done\"}]");

        assertThat(client.getAgentRunOutputs("run-1")).hasSize(1);
        assertThat(server.takeRequest().getPath()).isEqualTo("/api/agent-runs/run-1/outputs");
    }

    @Test
    void issuesAGitWorkspaceTicketAndFailsClosedWithoutOne() throws Exception {
        var request = new EmbabelAgentClient.GitWorkspaceTicketRequest("job-1", "https://git/x.git", "b", "wf-1", null);
        respond(200, "{\"ticket\":\"t-123\",\"expiresAt\":\"2026-10-16T10:00:00Z\"}");

        assertThat(client.issueGitWorkspaceTicketAs(AUTH, request)).isEqualTo("t-123");
        var post = server.takeRequest();
        assertThat(post.getPath()).isEqualTo("/internal/git-workspace-tickets");
        assertThat(post.getHeader("Authorization")).isEqualTo(AUTH);

        respond(200, "{\"ticket\":\" \"}");
        assertThatThrownBy(() -> client.issueGitWorkspaceTicketAs(AUTH, request))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no git workspace ticket");
        assertThat(new EmbabelAgentClient.GitWorkspaceTicketResponse("t-123", Instant.EPOCH).toString())
                .doesNotContain("t-123");
    }

    @Test
    void theAgentCatalogIsEmptyWhenTheAgentServiceIsUnreachable() throws IOException {
        respond(200, "{\"local\":{\"name\":\"Local\",\"status\":\"OK\",\"agents\":[]},\"providers\":[]}");
        assertThat(client.getAgentCatalog()).isPresent();

        server.shutdown();
        assertThat(client.getAgentCatalog()).isEmpty();
    }

    @Test
    void agentCardsRelayNotFoundAndTurnOtherFailuresIntoBadGateway() throws IOException {
        respond(200, "{\"name\":\"planner\"}");
        var ok = client.getProviderAgentCard("litellm", "planner");
        assertThat(ok.status()).isEqualTo(200);
        assertThat(ok.card()).containsEntry("name", "planner");

        respond(404, "{}");
        assertThat(client.getProviderAgentCard("litellm", "nope").status()).isEqualTo(404);
        respond(500, "{}");
        assertThat(client.getProviderAgentCard("litellm", "planner").status()).isEqualTo(502);
        server.enqueue(new MockResponse().setResponseCode(200));
        assertThat(client.getProviderAgentCard("litellm", "planner").status()).isEqualTo(502);

        server.shutdown();
        assertThat(client.getProviderAgentCard("litellm", "planner").status()).isEqualTo(502);
    }

    @Test
    void aRejectedA2AServiceUpdateKeepsItsStatusAndMessage() {
        respond(409, "{\"status\":409,\"message\":\"configured services are read-only\"}");

        assertThatThrownBy(() -> client.updateA2AService("litellm",
                new A2AServiceRequest(null, "LiteLLM", "http://litellm:4000", null, null, null, null, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).isEqualTo("configured services are read-only");
                });
    }

    @Test
    void theStartInputHidesItsSecrets() {
        var input = new WorkflowStartInputDto(null, "spec", "https://git/x.git", "p-1", "bot", "git-secret", "main")
                .withGitWorkspaceTicket("ticket-secret");

        assertThat(input.toString()).doesNotContain("git-secret").doesNotContain("ticket-secret").contains("p-1");
        assertThat(WorkflowStartInputDto.empty().toString()).contains("gitWorkspaceTicket=null");
    }
}
