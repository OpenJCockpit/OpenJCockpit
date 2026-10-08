package nl.metafactory.aicontrol;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import nl.metafactory.aicontrol.client.EmbabelAgentClient;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
class AiControlServiceApplicationTest {

    @MockitoBean
    EmbabelAgentClient embabelAgentClient;

    @Test
    void contextLoads() {
    }
}
