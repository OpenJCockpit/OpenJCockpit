package nl.metafactory.agents.definition.runtime;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.api.validation.guardrails.GuardRail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;

/**
 * Executes one configuration-driven agent run on Embabel. All inputs come from the
 * {@link PreparedAgent} snapshot: the model is selected by alias (Embabel LLM role), the
 * instructions arrive via {@link AgentPromptContributor}, skills via native Embabel
 * {@code Skills} references, tools only from the bound allowlist, and guardrails as Embabel
 * {@link GuardRail}s on the interaction.
 */
@Agent(description = "Runs a configuration-driven agent definition resolved for a tenant")
@Component
public class ConfiguredDefinitionAgent {

    private static final Logger log = LoggerFactory.getLogger(ConfiguredDefinitionAgent.class);

    public record Request(PreparedAgent agent, RunContext run, String message) {
    }

    public record Response(String agentId, String version, String contentHash, String traceId, String text) {
    }

    private final Ai ai;
    private final ToolCallAuthorizer toolCallAuthorizer;

    public ConfiguredDefinitionAgent(Ai ai, ToolCallAuthorizer toolCallAuthorizer) {
        this.ai = ai;
        this.toolCallAuthorizer = toolCallAuthorizer;
    }

    @Action(description = "Answer a message using the resolved agent definition")
    @AchievesGoal(description = "The message has been answered by the configured agent")
    public Response respond(Request request) {
        var definition = request.agent().definition();
        var run = request.run();
        if (!run.agentId().equals(definition.agentId()) || !run.contentHash().equals(definition.contentHash())
                || !run.tenantId().equals(definition.tenantId())) {
            throw new IllegalStateException("run context does not match the resolved snapshot");
        }
        var allowed = new HashSet<>(definition.toolIds());
        var tools = request.agent().tools().stream()
                .<Tool>map(t -> new SnapshotBoundTool(t, allowed, toolCallAuthorizer, run))
                .toList();

        log.info("agent.run.started {} source={} skills={} tools={} guardrails={}", run.logFields(),
                definition.source(), definition.skillIds(), definition.toolIds(), definition.guardrailIds());
        try (var skills = MaterializedSkills.of(definition.agentId(), request.agent().skills())) {
            var text = ai.withLlmByRole(definition.modelAlias())
                    .withPromptContributor(new AgentPromptContributor(definition))
                    .withReferences(skills.references())
                    .withTools(tools)
                    .withGuardRails(request.agent().guardRails().toArray(GuardRail[]::new))
                    .withToolNotFoundPolicy(new StrictToolNotFoundPolicy())
                    .generateText(request.message());
            log.info("agent.run.completed {}", run.logFields());
            return new Response(definition.agentId(), definition.version(), definition.contentHash(), run.traceId(), text);
        } catch (RuntimeException e) {
            log.warn("agent.run.failed {} error={}", run.logFields(), e.getClass().getSimpleName());
            throw e;
        }
    }
}
