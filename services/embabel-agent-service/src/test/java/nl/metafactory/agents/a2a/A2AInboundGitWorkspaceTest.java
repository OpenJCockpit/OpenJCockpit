package nl.metafactory.agents.a2a;

import nl.metafactory.agents.definition.config.AgentDefinitionProperties;
import nl.metafactory.agents.definition.runtime.AgentRunService;
import nl.metafactory.agents.definition.runtime.ConfiguredDefinitionAgent;
import nl.metafactory.agents.gitworkspace.GitWorkspaceBinding;
import nl.metafactory.agents.gitworkspace.GitWorkspaceProperties;
import nl.metafactory.agents.workflowrun.WorkflowRunScope;
import nl.metafactory.agents.workflowrun.WorkflowRunScopes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** An A2A-served agent gets its workflow run (and git workspace) only from the engine and only for an active run. */
class A2AInboundGitWorkspaceTest {

    private static final String SEND = """
            {"jsonrpc":"2.0","id":1,"method":"message/send","params":{"message":{"kind":"message","role":"user",
             "messageId":"m1","parts":[{"kind":"text","text":"implement it"}]}}}""";
    private static final WorkflowRunScope SCOPE = new WorkflowRunScope("wf-spec-realise", "run-7",
            new GitWorkspaceBinding("job-1", "https://github.com/org/repo.git", "openjcockpit/wf/abc"));

    private AgentRunService runs;
    private WorkflowRunScopes scopes;
    private A2AInboundAdapter adapter;

    @BeforeEach
    void setUp() {
        runs = mock(AgentRunService.class);
        when(runs.run(any(), any(), any(), any(), any(), any()))
                .thenReturn(new ConfiguredDefinitionAgent.Response("software-developer", "1.1.0", "h", "t", "done"));
        scopes = new WorkflowRunScopes();
        scopes.register(SCOPE);
        var properties = new AgentDefinitionProperties();
        adapter = new A2AInboundAdapter(runs, properties, scopes, new GitWorkspaceProperties());
    }

    private static Jwt token(String azp) {
        return Jwt.withTokenValue("t").header("alg", "RS256").subject("svc").claim("tenant_id", "local")
                .claim("azp", azp).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }

    @Test
    void theEnginesCallForAnActiveRunCarriesItsWorkflowScope() {
        adapter.handle("software-developer", SEND, token("openjcockpit-a2a"), null, "run-7");

        verify(runs).run(eq("local"), eq("software-developer"), any(), isNull(), eq("implement it"), eq(SCOPE));
    }

    @Test
    void anyOtherCallerOrAnUnknownRunGetsNoScope() {
        adapter.handle("software-developer", SEND, token("someone-else"), null, "run-7");
        adapter.handle("software-developer", SEND, token("openjcockpit-a2a"), null, "run-unknown");
        adapter.handle("software-developer", SEND, token("openjcockpit-a2a"), null, null);

        verify(runs, org.mockito.Mockito.times(3)).run(eq("local"), eq("software-developer"), any(), isNull(),
                eq("implement it"), isNull());
    }
}
