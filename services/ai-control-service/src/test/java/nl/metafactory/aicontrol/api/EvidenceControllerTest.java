package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.model.EvidenceOutput;
import nl.metafactory.aicontrol.model.EvidenceRun;
import nl.metafactory.aicontrol.model.EvidenceRunDetails;
import nl.metafactory.aicontrol.model.EvidenceRunPage;
import nl.metafactory.aicontrol.model.EvidenceSource;
import nl.metafactory.aicontrol.model.EvidenceStatus;
import nl.metafactory.aicontrol.model.EvidenceTimelineEvent;
import nl.metafactory.aicontrol.service.EvidenceService;
import nl.metafactory.aicontrol.service.evidenceexport.EvidenceExportJobService;
import nl.metafactory.aicontrol.service.evidenceexport.EvidenceExportService;
import nl.metafactory.aicontrol.model.EvidenceExportJob;
import nl.metafactory.aicontrol.model.EvidenceExportJobCreated;
import nl.metafactory.aicontrol.model.EvidenceExportJobStatus;
import nl.metafactory.aicontrol.model.EvidenceExportProgress;
import org.springframework.core.io.ByteArrayResource;
import nl.metafactory.aicontrol.model.EvidenceExportFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    @MockitoBean EvidenceExportService exportService;
    @MockitoBean EvidenceExportJobService jobService;

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

    private void stubExport(EvidenceExportFormat format, String ext, String body) {
        when(exportService.exportRun(PROJECT, "run-1", format, false)).thenReturn(
                new EvidenceExportService.ExportedFile("evidence-run-1." + ext, format.mediaType(), body.getBytes()));
    }

    @Test
    void exportSendsEachFormatAsAnAttachmentWithItsMediaType() throws Exception {
        stubExport(EvidenceExportFormat.JSON, "json", "{}");
        stubExport(EvidenceExportFormat.CSV, "csv", "a,b");
        stubExport(EvidenceExportFormat.MARKDOWN, "md", "# x");
        stubExport(EvidenceExportFormat.PDF, "pdf", "%PDF");

        for (var c : List.of(new String[]{"json", "application/json", "json"}, new String[]{"csv", "text/csv", "csv"},
                new String[]{"markdown", "text/markdown", "md"}, new String[]{"pdf", "application/pdf", "pdf"})) {
            mockMvc.perform(get("/api/projects/{id}/evidence/runs/run-1/export?format=" + c[0], PROJECT)
                            .accept(MediaType.APPLICATION_JSON))
                   .andExpect(status().isOk())
                   .andExpect(header().string(HttpHeaders.CONTENT_TYPE, org.hamcrest.Matchers.startsWith(c[1])))
                   .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                           "attachment; filename=\"evidence-run-1." + c[2] + "\""));
        }
        mockMvc.perform(get("/api/projects/{id}/evidence/runs/run-1/export?format=csv", PROJECT))
               .andExpect(content().string("a,b"));
    }

    @Test
    void exportPassesIncludeOutputs() throws Exception {
        when(exportService.exportRun(PROJECT, "run-1", EvidenceExportFormat.JSON, true)).thenReturn(
                new EvidenceExportService.ExportedFile("evidence-run-1.json", "application/json", "{}".getBytes()));

        mockMvc.perform(get("/api/projects/{id}/evidence/runs/run-1/export?format=json&includeOutputs=true", PROJECT))
               .andExpect(status().isOk());
    }

    @Test
    void exportWithUnknownOrMissingFormatIs400() throws Exception {
        mockMvc.perform(get("/api/projects/{id}/evidence/runs/run-1/export?format=xml", PROJECT))
               .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/projects/{id}/evidence/runs/run-1/export", PROJECT))
               .andExpect(status().isBadRequest());
        verifyNoInteractions(exportService);
    }

    @Test
    void exportOfARunInAnotherProjectIs404() throws Exception {
        when(exportService.exportRun(PROJECT, "x", EvidenceExportFormat.CSV, false))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/projects/{id}/evidence/runs/x/export?format=csv", PROJECT))
               .andExpect(status().isNotFound());
    }

    @Test
    void createJobIs202WithLocation() throws Exception {
        var job = UUID.randomUUID();
        when(jobService.create(org.mockito.ArgumentMatchers.eq(PROJECT), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new EvidenceExportJobCreated(job, EvidenceExportJobStatus.QUEUED));

        mockMvc.perform(post("/api/projects/{id}/evidence/exports", PROJECT)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"scope\":\"project\",\"format\":\"csv\"}"))
               .andExpect(status().isAccepted())
               .andExpect(header().string(HttpHeaders.LOCATION, "/api/projects/" + PROJECT + "/evidence/exports/" + job))
               .andExpect(jsonPath("$.jobId").value(job.toString()))
               .andExpect(jsonPath("$.status").value("queued"));
    }

    @Test
    void createJobPropagatesBadRequestAndTooManyRequests() throws Exception {
        when(jobService.create(org.mockito.ArgumentMatchers.eq(PROJECT), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "workflowId is required"))
                .thenThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "busy"));
        var req = post("/api/projects/{id}/evidence/exports", PROJECT)
                .contentType(MediaType.APPLICATION_JSON).content("{\"scope\":\"workflow\",\"format\":\"csv\"}");

        mockMvc.perform(req).andExpect(status().isBadRequest());
        mockMvc.perform(req).andExpect(status().isTooManyRequests());
    }

    @Test
    void jobStatusIsServed() throws Exception {
        var job = UUID.randomUUID();
        when(jobService.get(PROJECT, job)).thenReturn(new EvidenceExportJob(job, EvidenceExportJobStatus.RUNNING,
                new EvidenceExportProgress(12, 80), null, null, null, null));

        mockMvc.perform(get("/api/projects/{id}/evidence/exports/{job}", PROJECT, job))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status").value("running"))
               .andExpect(jsonPath("$.progress.runsDone").value(12))
               .andExpect(jsonPath("$.progress.runsTotal").value(80));
    }

    @Test
    void jobOfAnotherProjectOrUserIs404() throws Exception {
        var job = UUID.randomUUID();
        when(jobService.get(PROJECT, job)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "no"));

        mockMvc.perform(get("/api/projects/{id}/evidence/exports/{job}", PROJECT, job))
               .andExpect(status().isNotFound());
    }

    @Test
    void downloadSendsTheFileAsAttachment() throws Exception {
        var job = UUID.randomUUID();
        when(jobService.download(PROJECT, job)).thenReturn(new EvidenceExportJobService.DownloadFile(
                "evidence-wf-runs.csv", "text/csv", new ByteArrayResource("a,b\r\n".getBytes()), 5));

        mockMvc.perform(get("/api/projects/{id}/evidence/exports/{job}/download", PROJECT, job))
               .andExpect(status().isOk())
               .andExpect(content().contentTypeCompatibleWith("text/csv"))
               .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                       org.hamcrest.Matchers.containsString("evidence-wf-runs.csv")))
               .andExpect(content().string("a,b\r\n"));
    }

    @Test
    void downloadIs409BeforeDoneAnd410AfterExpiry() throws Exception {
        var job = UUID.randomUUID();
        when(jobService.download(PROJECT, job))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "not done"))
                .thenThrow(new ResponseStatusException(HttpStatus.GONE, "expired"));

        mockMvc.perform(get("/api/projects/{id}/evidence/exports/{job}/download", PROJECT, job))
               .andExpect(status().isConflict());
        mockMvc.perform(get("/api/projects/{id}/evidence/exports/{job}/download", PROJECT, job))
               .andExpect(status().isGone());
    }

    @Test
    void cancelIs204() throws Exception {
        var job = UUID.randomUUID();
        mockMvc.perform(delete("/api/projects/{id}/evidence/exports/{job}", PROJECT, job))
               .andExpect(status().isNoContent());
        org.mockito.Mockito.verify(jobService).cancel(PROJECT, job);
    }
}
