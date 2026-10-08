package nl.metafactory.aicontrol.config;

import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
class WebClientConfigTest {

    @Autowired WebClient embabelWebClient;
    @MockitoBean EmbabelAgentClient embabelAgentClient;

    @Test
    void embabelWebClientBeanIsConfigured() {
        assertThat(embabelWebClient).isNotNull();
    }
}
