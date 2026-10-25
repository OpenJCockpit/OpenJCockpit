package nl.metafactory.agentruntime;

import com.embabel.common.ai.model.ModelProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
class EmbabelAgentRuntimeApplicationTest {

    // Replaces the modelProvider that the embabel platform defines itself, so the context loads without models.
    @MockitoBean
    ModelProvider modelProvider;

    @Autowired
    ApplicationContext context;

    @Test
    void theRuntimeStartsWithoutADatabaseOrAnyManagementComponent() {
        assertThat(context.getBeanNamesForType(DataSource.class)).isEmpty();
        assertThat(context.containsBean("agentServiceClient")).isTrue();
        assertThat(context.containsBean("executionRegistry")).isTrue();
    }

    @Test
    void mainDelegatesToSpringApplicationRunWithoutStartingARealContext() {
        try (var springApplication = mockStatic(SpringApplication.class)) {
            String[] args = {"--spring.main.web-application-type=none"};

            EmbabelAgentRuntimeApplication.main(args);

            springApplication.verify(() -> SpringApplication.run(EmbabelAgentRuntimeApplication.class, args));
        }
    }
}
