package nl.metafactory.agents.definition.runtime;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.common.PromptRunner;
import com.embabel.agent.api.reference.LlmReference;
import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.api.validation.guardrails.GuardRail;
import com.embabel.agent.spi.loop.ToolNotFoundAction;
import com.embabel.agent.spi.loop.ToolNotFoundPolicy;
import com.embabel.common.ai.prompt.PromptContributor;
import nl.metafactory.agents.definition.guardrail.PiiProtectionGuardrail;
import nl.metafactory.agents.definition.model.DefinitionSource;
import nl.metafactory.agents.definition.model.ResolvedAgentDefinition;
import nl.metafactory.agents.definition.skill.LocalSkillInventory;
import nl.metafactory.agents.definition.skill.SkillMarkdownParser;
import nl.metafactory.agents.definition.source.ConfinedRoot;
import nl.metafactory.agents.definition.tool.RegisteredAgentTool;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Answers;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfiguredDefinitionAgentTest {

    static final String HASH = "ab".repeat(32);

    final AtomicInteger readCalls = new AtomicInteger();
    final RegisteredAgentTool customerRead = new RegisteredAgentTool("customer.read",
            Tool.create("customer_read", "read customer", in -> {
                readCalls.incrementAndGet();
                return Tool.Result.text("status A");
            }));

    static PreparedAgent prepared(RegisteredAgentTool tool) {
        var skill = new LocalSkillInventory(new ConfinedRoot(Path.of("src/test/resources/agent-definitions")),
                new SkillMarkdownParser()).lookup("customer-lookup").skill();
        var def = new ResolvedAgentDefinition("customer-support", "tenant-a", "# Rol\nHelp.", "customer-chat",
                List.of("customer-lookup"), List.of("customer.read"), List.of("pii-protection"), DefinitionSource.LOCAL,
                "1.0.0", HASH, Instant.now(), Map.of("pii-protection", "embabel-guardrail:PiiProtectionGuardrail"),
                List.of());
        return new PreparedAgent(def, List.of(skill), List.of(tool), new PiiProtectionGuardrail().embabelGuardRails(), null);
    }

    static RunContext run() {
        return new RunContext("run-1", "trace-1", "tenant-a", "customer-support", "1.0.0", HASH, "alice");
    }

    @Test
    @SuppressWarnings("unchecked")
    void wiresSnapshotIntoEmbabelPromptRunner() {
        var ai = mock(Ai.class);
        var runner = mock(PromptRunner.class, Answers.RETURNS_SELF);
        when(ai.withLlmByRole("customer-chat")).thenReturn(runner);
        when(runner.generateText(anyString())).thenReturn("Uw account is actief.");
        var agent = new ConfiguredDefinitionAgent(ai, (r, t) -> ToolCallAuthorizer.Decision.allow());

        var response = agent.respond(new ConfiguredDefinitionAgent.Request(prepared(customerRead), run(), "Hoe staat mijn account?"));

        assertThat(response.text()).isEqualTo("Uw account is actief.");
        assertThat(response.contentHash()).isEqualTo(HASH);
        assertThat(response.traceId()).isEqualTo("trace-1");

        var contributor = ArgumentCaptor.forClass(PromptContributor.class);
        verify(runner).withPromptContributor(contributor.capture());
        assertThat(contributor.getValue().contribution()).contains("# Rol\nHelp.");

        var references = ArgumentCaptor.forClass(List.class);
        verify(runner).withReferences(references.capture());
        assertThat(((List<LlmReference>) references.getValue())).extracting(LlmReference::getName)
                .contains("customer-lookup");

        var tools = ArgumentCaptor.forClass(List.class);
        verify(runner).withTools(tools.capture());
        var boundTools = (List<Tool>) tools.getValue();
        assertThat(boundTools).extracting(t -> t.getDefinition().getName()).containsExactly("customer_read");
        assertThat(boundTools.getFirst().call("{}").toString()).contains("status A");
        assertThat(readCalls).hasValue(1);

        var guards = ArgumentCaptor.forClass(GuardRail.class);
        verify(runner).withGuardRails(guards.capture(), guards.capture());
        assertThat(guards.getAllValues()).extracting(GuardRail::getName)
                .containsExactly("pii-protection:input", "pii-protection:output");

        var notFound = ArgumentCaptor.forClass(ToolNotFoundPolicy.class);
        verify(runner).withToolNotFoundPolicy(notFound.capture());
        assertThat(notFound.getValue().handle("customer_delete", boundTools)).isInstanceOf(ToolNotFoundAction.Throw.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void policyDenialAtCallTimeNeverReachesTheTool() {
        var ai = mock(Ai.class);
        var runner = mock(PromptRunner.class, Answers.RETURNS_SELF);
        when(ai.withLlmByRole("customer-chat")).thenReturn(runner);
        when(runner.generateText(anyString())).thenReturn("x");
        var agent = new ConfiguredDefinitionAgent(ai, (r, t) -> ToolCallAuthorizer.Decision.deny("no"));

        agent.respond(new ConfiguredDefinitionAgent.Request(prepared(customerRead), run(), "hi"));

        var tools = ArgumentCaptor.forClass(List.class);
        verify(runner).withTools(tools.capture());
        var result = ((List<Tool>) tools.getValue()).getFirst().call("{}");
        assertThat(result).isInstanceOf(Tool.Result.Error.class);
        assertThat(readCalls).hasValue(0);
    }

    @Test
    void runContextMustMatchTheSnapshot() {
        var agent = new ConfiguredDefinitionAgent(mock(Ai.class), (r, t) -> ToolCallAuthorizer.Decision.allow());
        var otherTenant = new RunContext("run-1", "trace-1", "tenant-b", "customer-support", "1.0.0", HASH, "alice");
        assertThatThrownBy(() -> agent.respond(new ConfiguredDefinitionAgent.Request(prepared(customerRead), otherTenant, "hi")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aFailingRunIsPassedOnAfterItsSkillsAreCleanedUp() {
        var ai = mock(Ai.class);
        var runner = mock(PromptRunner.class, Answers.RETURNS_SELF);
        when(ai.withLlmByRole("customer-chat")).thenReturn(runner);
        when(runner.generateText(anyString())).thenThrow(new IllegalStateException("model gateway down"));
        var agent = new ConfiguredDefinitionAgent(ai, (r, t) -> ToolCallAuthorizer.Decision.allow());

        assertThatThrownBy(() -> agent.respond(new ConfiguredDefinitionAgent.Request(prepared(customerRead), run(), "Hi")))
                .isInstanceOf(IllegalStateException.class).hasMessage("model gateway down");
    }

    @Test
    void preparedSkillsAndToolsMustMatchTheSnapshot() {
        var def = prepared(customerRead).definition();
        var skills = prepared(customerRead).skills();

        assertThatThrownBy(() -> new PreparedAgent(def, List.of(), List.of(customerRead), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("skill packages do not match the snapshot");
        assertThatThrownBy(() -> new PreparedAgent(def, skills, List.of(), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("bound tools do not match the snapshot");
    }
}
