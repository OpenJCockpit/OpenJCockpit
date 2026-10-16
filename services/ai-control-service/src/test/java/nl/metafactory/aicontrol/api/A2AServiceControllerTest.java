package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.client.A2AServiceDto;
import nl.metafactory.aicontrol.client.A2AServiceRequest;
import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.common.test.UnsecuredWebMvcTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@UnsecuredWebMvcTest(A2AServiceController.class)
class A2AServiceControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean EmbabelAgentClient client;

    static A2AServiceDto service() {
        return new A2AServiceDto("acme", "Acme", "litellm", "DASHBOARD", true, "https://gw.example", null, true,
                null, null, false, "OK", null, 2, null);
    }

    static final String BODY = """
            {"id":"acme","name":"Acme","baseUrl":"https://gw.example","apiKey":"sk"}""";

    @Test
    void relaysListCreateUpdateAndDelete() throws Exception {
        when(client.listA2AServices()).thenReturn(List.of(service()));
        mockMvc.perform(get("/api/a2a-services")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("acme"))
                .andExpect(jsonPath("$[0].agentCount").value(2));

        when(client.createA2AService(any())).thenReturn(service());
        mockMvc.perform(post("/api/a2a-services").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.editable").value(true));
        verify(client).createA2AService(new A2AServiceRequest("acme", "Acme", "https://gw.example", null, "sk", null, null, null));

        when(client.updateA2AService(eq("acme"), any())).thenReturn(service());
        mockMvc.perform(put("/api/a2a-services/acme").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/a2a-services/acme")).andExpect(status().isNoContent());
        verify(client).deleteA2AService("acme");
    }

    @Test
    void agentServiceRejectionsKeepTheirStatus() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "used by workflow wf-1"))
                .when(client).deleteA2AService("acme");
        mockMvc.perform(delete("/api/a2a-services/acme")).andExpect(status().isConflict());
    }
}
