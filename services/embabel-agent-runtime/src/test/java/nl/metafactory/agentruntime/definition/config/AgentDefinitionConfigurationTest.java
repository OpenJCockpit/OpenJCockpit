package nl.metafactory.agentruntime.definition.config;

import com.embabel.agent.spi.support.springai.SpringAiLlmService;
import nl.metafactory.agentruntime.definition.guardrail.GuardrailPolicyEnforcer;
import nl.metafactory.agentruntime.definition.guardrail.PiiProtectionGuardrail;
import nl.metafactory.common.agentdefinition.AgentMarkdownParser;
import nl.metafactory.agentruntime.definition.resolver.AgentDefinitionResolver;
import nl.metafactory.agentruntime.definition.resolver.ModelAliasCatalog;
import nl.metafactory.agentruntime.definition.runtime.AgentRunService;
import nl.metafactory.agentruntime.definition.runtime.ConfiguredDefinitionAgent;
import nl.metafactory.agentruntime.definition.skill.SkillMarkdownParser;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AgentDefinitionConfigurationTest {

    @Configuration
    @EnableConfigurationProperties(AgentDefinitionProperties.class)
    @Import({AgentDefinitionConfiguration.class, SkillMarkdownParser.class, PiiProtectionGuardrail.class})
    static class Infra {
        @Bean
        nl.metafactory.agentruntime.management.AgentServiceClient agentServiceClient() {
            return mock(nl.metafactory.agentruntime.management.AgentServiceClient.class);
        }

        @Bean
        ConfiguredDefinitionAgent configuredDefinitionAgent() {
            return mock(ConfiguredDefinitionAgent.class);
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Infra.class);

    @Test
    void disabledByDefaultCreatesNothing() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).doesNotHaveBean(AgentDefinitionResolver.class);
            assertThat(ctx).doesNotHaveBean(SpringAiLlmService.class);
        });
    }

    @Test
    void enabledWiresResolverRunServiceAndGatewayAliasModel() {
        runner.withPropertyValues(
                "openjcockpit.agent-definitions.enabled=true",
                "openjcockpit.agent-definitions.local-root=src/test/resources/agent-definitions",
                "openjcockpit.agent-definitions.litellm.gateway-model-aliases=customer-chat",
                "openjcockpit.agent-definitions.litellm.model-gateway-url=http://litellm:4000/v1",
                "openjcockpit.agent-definitions.litellm.model-gateway-key=sk-test",
                "embabel.models.llms.customer-chat=customer-chat"
        ).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(AgentDefinitionResolver.class).hasSingleBean(AgentRunService.class)
                    .hasSingleBean(nl.metafactory.agentruntime.definition.runtime.CatalogAgentRuns.class)
                    .hasSingleBean(AgentMarkdownParser.class);
            assertThat(ctx.getBean(nl.metafactory.agentruntime.definition.binding.AgentTenantBindings.class))
                    .isInstanceOf(nl.metafactory.agentruntime.definition.binding.RemoteAgentTenantBindings.class);
            assertThat(ctx.getBean(ModelAliasCatalog.class).contains("customer-chat")).isTrue();
            assertThat(ctx).hasBean("agentGatewayLlm-customer-chat");
            assertThat(ctx.getBean("agentGatewayLlm-customer-chat", SpringAiLlmService.class).getName())
                    .isEqualTo("customer-chat");
            assertThat(ctx.getBean(GuardrailPolicyEnforcer.class).enforce("x", "customer-chat",
                    java.util.List.of("pii-protection")).executors()).containsKey("pii-protection");
        });
    }

    @Test
    void gatewayGuardrailWithoutLiteLlmFailsStartup() {
        runner.withPropertyValues(
                "openjcockpit.agent-definitions.enabled=true",
                "openjcockpit.agent-definitions.local-root=src/test/resources/agent-definitions",
                "openjcockpit.agent-definitions.litellm.gateway-guardrails.gateway-pii-filter=openjcockpit-pii"
        ).run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void aRegistryUrlWithoutKeyIsARemoteSourceButCannotVerifyGatewayGuardrails() {
        runner.withPropertyValues(
                "openjcockpit.agent-definitions.enabled=true",
                "openjcockpit.agent-definitions.local-root=src/test/resources/agent-definitions",
                "openjcockpit.agent-definitions.litellm.registry-url=http://litellm:4000",
                "openjcockpit.agent-definitions.litellm.gateway-guardrails.gateway-pii-filter=openjcockpit-pii"
        ).run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues(
                "openjcockpit.agent-definitions.enabled=true",
                "openjcockpit.agent-definitions.local-root=src/test/resources/agent-definitions",
                "openjcockpit.agent-definitions.litellm.registry-url= ",
                "openjcockpit.agent-definitions.litellm.registry-key=sk-test"
        ).run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    void gatewayAliasWithoutUrlFailsStartup() {
        runner.withPropertyValues(
                "openjcockpit.agent-definitions.enabled=true",
                "openjcockpit.agent-definitions.local-root=src/test/resources/agent-definitions",
                "openjcockpit.agent-definitions.litellm.gateway-model-aliases=customer-chat"
        ).run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void aCacheDirectoryAndAConfiguredLiteLlmRegistryAddTheSnapshotStoreRemoteSourcesAndGatewayGuardrails(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path cache) {
        runner.withPropertyValues(
                "openjcockpit.agent-definitions.enabled=true",
                "openjcockpit.agent-definitions.local-root=src/test/resources/agent-definitions",
                "openjcockpit.agent-definitions.cache.directory=" + cache,
                "openjcockpit.agent-definitions.litellm.gateway-guardrails.gateway-pii-filter=openjcockpit-pii",
                "openjcockpit.agent-definitions.litellm.registry-url=http://litellm:4000",
                "openjcockpit.agent-definitions.litellm.registry-key=sk-test"
        ).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(nl.metafactory.agentruntime.definition.cache.LastKnownGoodStore.class);
            // registered through LiteLLM (not "no executor"), but unproven without a model alias
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> ctx.getBean(GuardrailPolicyEnforcer.class)
                            .enforce("x", java.util.List.of("gateway-pii-filter")))
                    .hasMessageContaining("litellm-policy:openjcockpit-pii is not operational");
        });
    }

    @Test
    void aGatewayAliasThatIsNotKebabCaseFailsStartup() {
        runner.withPropertyValues(
                "openjcockpit.agent-definitions.enabled=true",
                "openjcockpit.agent-definitions.local-root=src/test/resources/agent-definitions",
                "openjcockpit.agent-definitions.litellm.gateway-model-aliases=Customer_Chat",
                "openjcockpit.agent-definitions.litellm.model-gateway-url=http://litellm:4000/v1",
                "openjcockpit.agent-definitions.litellm.model-gateway-key=sk-test"
        ).run(ctx -> assertThat(ctx).hasFailed());
    }
}
