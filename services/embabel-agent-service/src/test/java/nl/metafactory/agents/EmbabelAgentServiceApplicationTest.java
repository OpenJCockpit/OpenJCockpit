package nl.metafactory.agents;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
class EmbabelAgentServiceApplicationTest {


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
