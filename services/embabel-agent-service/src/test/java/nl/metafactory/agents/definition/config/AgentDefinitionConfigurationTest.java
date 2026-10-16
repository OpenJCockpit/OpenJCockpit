package nl.metafactory.agents.definition.config;

import com.embabel.agent.spi.support.springai.SpringAiLlmService;
import nl.metafactory.agents.definition.guardrail.GuardrailPolicyEnforcer;
import nl.metafactory.agents.definition.guardrail.PiiProtectionGuardrail;
import nl.metafactory.agents.definition.markdown.AgentMarkdownParser;
import nl.metafactory.agents.definition.resolver.AgentDefinitionResolver;
import nl.metafactory.agents.definition.resolver.ModelAliasCatalog;
import nl.metafactory.agents.definition.runtime.AgentRunService;
import nl.metafactory.agents.definition.runtime.ConfiguredDefinitionAgent;
import nl.metafactory.agents.definition.skill.SkillMarkdownParser;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AgentDefinitionConfigurationTest {

    @Configuration
    @EnableConfigurationProperties(AgentDefinitionProperties.class)
    @Import({AgentDefinitionConfiguration.class, AgentMarkdownParser.class, SkillMarkdownParser.class,
            PiiProtectionGuardrail.class, nl.metafactory.agents.a2a.client.A2AProvidersProperties.class,
            nl.metafactory.agents.a2a.client.A2AProviderClients.class,
            nl.metafactory.agents.catalog.AgentCatalogProperties.class, nl.metafactory.agents.catalog.LocalAgentCatalog.class})
    static class Infra {
        @Bean
        JdbcClient jdbcClient() {
            return mock(JdbcClient.class);
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
            assertThat(ctx).hasSingleBean(AgentDefinitionResolver.class).hasSingleBean(AgentRunService.class);
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
    void gatewayAliasWithoutUrlFailsStartup() {
        runner.withPropertyValues(
                "openjcockpit.agent-definitions.enabled=true",
                "openjcockpit.agent-definitions.local-root=src/test/resources/agent-definitions",
                "openjcockpit.agent-definitions.litellm.gateway-model-aliases=customer-chat"
        ).run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void aCacheDirectoryAndAConfiguredLiteLlmProviderAddTheSnapshotStoreRemoteSourcesAndGatewayGuardrails(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path cache) {
        runner.withPropertyValues(
                "openjcockpit.agent-definitions.enabled=true",
                "openjcockpit.agent-definitions.local-root=src/test/resources/agent-definitions",
                "openjcockpit.agent-definitions.cache.directory=" + cache,
                "openjcockpit.agent-definitions.litellm.gateway-guardrails.gateway-pii-filter=openjcockpit-pii",
                "openjcockpit.a2a-providers[0].type=litellm",
                "openjcockpit.a2a-providers[0].base-url=http://litellm:4000",
                "openjcockpit.a2a-providers[0].api-key=sk-test"
        ).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(nl.metafactory.agents.definition.cache.LastKnownGoodStore.class);
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
