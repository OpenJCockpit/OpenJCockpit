package nl.metafactory.agents;

import com.embabel.common.ai.model.ModelProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
class EmbabelAgentServiceApplicationTest {

    // Replaces the modelProvider that the embabel platform defines itself since 0.2.0,
    // so the context loads without a real model configuration.
    @MockitoBean
    ModelProvider modelProvider;

    @Test
    void contextLoads() {
    }

    @Test
    void mainDelegatesToSpringApplicationRunWithoutStartingARealContext() {
        try (var springApplication = mockStatic(SpringApplication.class)) {
            String[] args = {"--spring.main.web-application-type=none"};

            EmbabelAgentServiceApplication.main(args);

            springApplication.verify(() -> SpringApplication.run(EmbabelAgentServiceApplication.class, args));
        }
    }
}
