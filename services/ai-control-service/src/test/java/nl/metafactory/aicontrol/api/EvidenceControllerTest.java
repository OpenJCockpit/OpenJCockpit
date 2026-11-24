package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.model.EvidenceOutput;
import nl.metafactory.aicontrol.model.EvidenceRun;
import nl.metafactory.aicontrol.model.EvidenceRunDetails;
import nl.metafactory.aicontrol.model.EvidenceRunPage;
import nl.metafactory.aicontrol.model.EvidenceSource;
import nl.metafactory.aicontrol.model.EvidenceStatus;
import nl.metafactory.aicontrol.model.EvidenceTimelineEvent;
import nl.metafactory.aicontrol.service.EvidenceService;
import nl.metafactory.common.test.UnsecuredWebMvcTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@UnsecuredWebMvcTest(EvidenceController.class)
class EvidenceControllerTest {

    private static final UUID PROJECT = UUID.fromString("11111111-0000-4000-a000-000000000001");

    @Autowired MockMvc mockMvc;
    @MockitoBean EvidenceService evidenceService;

    private EvidenceRun run() {
        var details = new EvidenceRunDetails("run-1", "wf", "OK", "alice", Instant.parse("2026-01-01T10:00:00Z"),
                null, "feature/x", null, List.of(), null);
        var event = new EvidenceTimelineEvent(Instant.parse("2026-01-01T10:00:05Z"), EvidenceSource.AGENT,
                "impl", null, "Done", null, "impl", EvidenceStatus.OK);
        return new EvidenceRun(details, List.of(event));
    }

    @Test
    void listRunsUsesDefaultPaging() throws Exception {
        when(evidenceService.listRuns(PROJECT, 20, 0)).thenReturn(new EvidenceRunPage(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/projects/{id}/evidence/runs", PROJECT))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.items").isEmpty())
               .andExpect(jsonPath("$.limit").value(20));
    }

    @Test
    void listRunsPassesExplicitPaging() throws Exception {
        when(evidenceService.listRuns(PROJECT, 5, 10)).thenReturn(new EvidenceRunPage(List.of(), 12, 5, 10));

        mockMvc.perform(get("/api/projects/{id}/evidence/runs?limit=5&offset=10", PROJECT))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.offset").value(10));
    }

    @Test
    void getRunSerialisesEnumsAsLowercaseValues() throws Exception {
        when(evidenceService.getRun(PROJECT, "run-1")).thenReturn(run());

        mockMvc.perform(get("/api/projects/{id}/evidence/runs/run-1", PROJECT))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.details.runId").value("run-1"))
               .andExpect(jsonPath("$.events[0].source").value("agent"))
               .andExpect(jsonPath("$.events[0].status").value("ok"))
               .andExpect(jsonPath("$.events[0].outputRef").value("impl"));
    }

    @Test
    void getRunOfAnotherProjectIs404() throws Exception {
        when(evidenceService.getRun(PROJECT, "x")).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/projects/{id}/evidence/runs/x", PROJECT)).andExpect(status().isNotFound());
    }

    @Test
    void latestRunIsServed() throws Exception {
        when(evidenceService.getLatestRun(PROJECT)).thenReturn(run());

        mockMvc.perform(get("/api/projects/{id}/evidence/latest", PROJECT))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.details.status").value("OK"));
    }

    @Test
    void outputIsServed() throws Exception {
        when(evidenceService.getOutput(PROJECT, "run-1", "impl")).thenReturn(new EvidenceOutput("impl", "text"));

        mockMvc.perform(get("/api/projects/{id}/evidence/runs/run-1/outputs/impl", PROJECT))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.output").value("text"));
    }
}
