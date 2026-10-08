package nl.metafactory.aicontrol.config;

import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.integration.http.GuardedHttpGateway;
import nl.metafactory.aicontrol.integration.skillsmarketplace.placeholder.PlaceholderSkillsMarketplaceClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The executable proof for AC-27b / BR-9: {@code embabelWebClient} remains the module's only
 * {@code WebClient} bean, {@code skillsMarketplaceHttpClient} is the module's only {@code
 * java.net.http.HttpClient} bean, and {@link GuardedHttpGateway} holds no field or constructor
 * parameter assignable to a WebClient-relay-capable type, and so does {@link
 * PlaceholderSkillsMarketplaceClient} (D5 — closes AC-27b).
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
class SkillsMarketplaceEgressWiringTest {

    @Autowired
    ApplicationContext context;

    @MockitoBean
    EmbabelAgentClient embabelAgentClient;

    @Test
    void webClientBeanNamesAreExactlyEmbabelWebClient() {
        assertThat(context.getBeanNamesForType(WebClient.class)).containsExactly("embabelWebClient");
    }

    @Test
    void httpClientBeanNamesAreExactlySkillsMarketplaceHttpClient() {
        assertThat(context.getBeanNamesForType(HttpClient.class)).containsExactly("skillsMarketplaceHttpClient");
    }

    @Test
    void guardedHttpGatewayHasNoRelayCapableFieldOrConstructorParameter() {
        assertNoRelayCapableReference(GuardedHttpGateway.class);
    }

    @Test
    void placeholderSkillsMarketplaceClientHasNoRelayCapableFieldOrConstructorParameter() {
        assertNoRelayCapableReference(PlaceholderSkillsMarketplaceClient.class);
    }

    static void assertNoRelayCapableReference(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            assertThat(isRelayCapable(field.getType()))
                    .as("field %s of %s must not be WebClient/ExchangeFilterFunction/RestClient-assignable",
                            field.getName(), type.getSimpleName())
                    .isFalse();
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            for (Class<?> paramType : constructor.getParameterTypes()) {
                assertThat(isRelayCapable(paramType))
                        .as("constructor parameter of type %s in %s must not be WebClient/ExchangeFilterFunction/RestClient-assignable",
                                paramType.getSimpleName(), type.getSimpleName())
                        .isFalse();
            }
        }
    }

    private static boolean isRelayCapable(Class<?> type) {
        return WebClient.class.isAssignableFrom(type)
                || ExchangeFilterFunction.class.isAssignableFrom(type)
                || RestClient.class.isAssignableFrom(type);
    }
}
