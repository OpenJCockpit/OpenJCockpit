package nl.metafactory.agents.definition.config;

import nl.metafactory.agents.definition.config.AgentDefinitionProperties.SourceKind;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentDefinitionPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(AgentDefinitionProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Config.class);

    @Test
    void defaultsAreDisabledAndFailSafe() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            var p = ctx.getBean(AgentDefinitionProperties.class);
            assertThat(p.isEnabled()).isFalse();
            assertThat(p.getSourcePriority())
                    .containsExactly(SourceKind.REMOTE, SourceKind.LAST_KNOWN_GOOD, SourceKind.LOCAL);
            assertThat(p.getFallback().isOnUnavailable()).isTrue();
            assertThat(p.getFallback().isOnNotFound()).isFalse();
            assertThat(p.getA2a().isEnabled()).isFalse();
        });
    }

    @Test
    void bindsAValidConfiguration() {
        runner.withPropertyValues(
                "openjcockpit.agent-definitions.enabled=true",
                "openjcockpit.agent-definitions.local-root=/srv/agent-definitions",
                "openjcockpit.agent-definitions.source-priority=LOCAL,REMOTE",
                "openjcockpit.agent-definitions.fallback.on-not-found=true",
                "openjcockpit.agent-definitions.cache.ttl=15m",
                "openjcockpit.agent-definitions.cache.max-entries=10",
                "openjcockpit.agent-definitions.litellm.model-gateway-url=http://litellm:4000/v1",
                "openjcockpit.agent-definitions.a2a.enabled=true",
                "openjcockpit.agent-definitions.a2a.public-base-url=http://embabel:8081"
        ).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            var p = ctx.getBean(AgentDefinitionProperties.class);
            assertThat(p.getLocalRoot()).isEqualTo("/srv/agent-definitions");
            assertThat(p.getSourcePriority()).containsExactly(SourceKind.LOCAL, SourceKind.REMOTE);
            assertThat(p.getFallback().isOnNotFound()).isTrue();
            assertThat(p.getCache().getTtl()).isEqualTo(Duration.ofMinutes(15));
            assertThat(p.getLitellm().getModelGatewayUrl()).isEqualTo("http://litellm:4000/v1");
            assertThat(p.getA2a().getPublicBaseUrl()).isEqualTo("http://embabel:8081");
        });
    }

    @Test
    void enabledWithoutLocalRootFailsStartup() {
        runner.withPropertyValues("openjcockpit.agent-definitions.enabled=true")
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("local-root is required"));
    }

    @Test
    void a2aEnabledWithoutPublicUrlFailsStartup() {
        runner.withPropertyValues("openjcockpit.agent-definitions.a2a.enabled=true",
                        "openjcockpit.agent-definitions.enabled=true",
                        "openjcockpit.agent-definitions.local-root=/srv/x")
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("public-base-url is required"));
        runner.withPropertyValues("openjcockpit.agent-definitions.a2a.enabled=true",
                        "openjcockpit.agent-definitions.a2a.public-base-url=http://x")
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("requires openjcockpit.agent-definitions.enabled"));
    }

    @Test
    void duplicateSourcePriorityFailsStartup() {
        runner.withPropertyValues("openjcockpit.agent-definitions.source-priority=LOCAL,LOCAL")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void apiKeyIsNeverRenderedByToString() {
        var litellm = new AgentDefinitionProperties.LiteLlm();
        litellm.setModelGatewayKey("sk-secret");
        assertThat(litellm.toString()).doesNotContain("sk-secret");
        assertThat(List.of(litellm.toString())).allMatch(s -> s.contains("***"));
    }

    @Test
    void bindsTheCacheMandatoryGuardrailsAndGatewayKeyWithoutPrintingTheKey() {
        runner.withPropertyValues(
                "openjcockpit.agent-definitions.cache.directory=/var/cache/agents",
                "openjcockpit.agent-definitions.cache.max-entries=7",
                "openjcockpit.agent-definitions.mandatory-guardrails=pii-protection",
                "openjcockpit.agent-definitions.litellm.model-gateway-key=sk-secret"
        ).run(ctx -> {
            var p = ctx.getBean(AgentDefinitionProperties.class);
            assertThat(p.getCache().getDirectory()).isEqualTo("/var/cache/agents");
            assertThat(p.getCache().getMaxEntries()).isEqualTo(7);
            assertThat(p.getMandatoryGuardrails()).containsExactly("pii-protection");
            assertThat(p.getLitellm().getModelGatewayKey()).isEqualTo("sk-secret");
            assertThat(p.getLitellm().toString()).doesNotContain("sk-secret");
        });
    }
}
