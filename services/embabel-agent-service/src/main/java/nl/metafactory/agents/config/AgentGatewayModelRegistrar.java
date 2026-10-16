package nl.metafactory.agents.config;

import com.embabel.agent.spi.support.springai.SpringAiLlmService;
import io.micrometer.observation.ObservationRegistry;
import nl.metafactory.agents.definition.markdown.Identifiers;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import java.util.List;

/**
 * SPEC-002: registers one gateway-routed Embabel LLM service per configured agent model alias
 * ({@code openjcockpit.agent-definitions.litellm.gateway-model-aliases}). Each alias is sent to
 * LiteLLM as its own {@code model}, so a LiteLLM policy attachment ({@code models: [alias]}) selects
 * exactly this traffic. The Embabel role of the same name must map to it
 * ({@code embabel.models.llms.<alias>: <alias>}).
 */
public class AgentGatewayModelRegistrar implements BeanDefinitionRegistryPostProcessor {

    static final String PREFIX = "openjcockpit.agent-definitions.litellm.";

    private final Environment environment;

    public AgentGatewayModelRegistrar(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        var binder = Binder.get(environment);
        List<String> aliases = binder.bind(PREFIX + "gateway-model-aliases", Bindable.listOf(String.class))
                .orElse(List.of());
        if (aliases.isEmpty()) {
            return;
        }
        String url = binder.bind(PREFIX + "model-gateway-url", String.class)
                .orElseThrow(() -> new IllegalStateException(PREFIX + "model-gateway-url is required for gateway model aliases"));
        String key = binder.bind(PREFIX + "model-gateway-key", String.class)
                .orElseThrow(() -> new IllegalStateException(PREFIX + "model-gateway-key is required for gateway model aliases"));
        long connect = binder.bind("openjcockpit.llm.connect-timeout-seconds", Long.class).orElse(45L);
        long read = binder.bind("openjcockpit.llm.read-timeout-seconds", Long.class).orElse(600L);
        for (String alias : aliases) {
            if (!Identifiers.isSlug(alias)) {
                throw new IllegalStateException("gateway model alias must be kebab-case: " + alias);
            }
            var definition = new RootBeanDefinition(SpringAiLlmService.class);
            definition.setInstanceSupplier(() -> LiteLlmGatewayModelConfig.gatewayLlm(url, key, alias, connect, read,
                    ((ConfigurableListableBeanFactory) registry).getBeanProvider(ObservationRegistry.class)
                            .getIfAvailable(() -> ObservationRegistry.NOOP)));
            registry.registerBeanDefinition("agentGatewayLlm-" + alias, definition);
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        // no-op
    }
}
