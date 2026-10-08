package nl.metafactory.aicontrol.config;

import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CorsConfigTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean EmbabelAgentClient embabelAgentClient;

    @Test
    void corsAllowsLocalhostFrontend() throws Exception {
        mockMvc.perform(options("/api/workspaces/test")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET"))
               .andExpect(status().isOk())
               .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void corsAllowsLocalhostDashboard() throws Exception {
        mockMvc.perform(options("/api/workspaces/test")
                .header("Origin", "http://localhost:4000")
                .header("Access-Control-Request-Method", "GET"))
               .andExpect(status().isOk())
               .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4000"));
    }
}
