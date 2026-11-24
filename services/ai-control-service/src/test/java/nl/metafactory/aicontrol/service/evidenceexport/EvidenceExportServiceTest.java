package nl.metafactory.aicontrol.service.evidenceexport;

import nl.metafactory.aicontrol.client.AgentRunOutputDto;
import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.model.EvidenceExportFormat;
import nl.metafactory.aicontrol.model.EvidenceExportRun;
import nl.metafactory.aicontrol.model.EvidenceRun;
import nl.metafactory.aicontrol.model.EvidenceSource;
import nl.metafactory.aicontrol.service.EvidenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static nl.metafactory.aicontrol.service.evidenceexport.ExportFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EvidenceExportServiceTest {

    private final UUID project = UUID.randomUUID();
    private EvidenceService evidenceService;
    private EmbabelAgentClient embabel;
    private EvidenceExportService service;

    @BeforeEach
    void setUp() {
        evidenceService = mock(EvidenceService.class);
        embabel = mock(EmbabelAgentClient.class);
        service = new EvidenceExportService(evidenceService, embabel, JsonMapper.builder().build(),
                Clock.fixed(T0, ZoneOffset.UTC), 10, 15);
        when(evidenceService.getRun(project, "run-1")).thenReturn(new EvidenceRun(details("run-1"),
                List.of(event(EvidenceSource.AGENT, "Done", null), event(EvidenceSource.GIT, "Pushed", null))));
    }

    @Test
    void exportsARunInEachTextFormatWithFilenameAndMediaType() {
        var json = service.exportRun(project, "run-1", EvidenceExportFormat.JSON, false);
        var csv = service.exportRun(project, "run-1", EvidenceExportFormat.CSV, false);
        var md = service.exportRun(project, "run-1", EvidenceExportFormat.MARKDOWN, false);

        assertThat(json.filename()).isEqualTo("evidence-run-1.json");
        assertThat(json.mediaType()).isEqualTo("application/json");
        assertThat(new String(json.content(), StandardCharsets.UTF_8)).contains("\"runId\" : \"run-1\"");
        assertThat(csv.filename()).isEqualTo("evidence-run-1.csv");
        assertThat(csv.mediaType()).isEqualTo("text/csv");
        assertThat(new String(csv.content(), StandardCharsets.UTF_8).split("\r\n")).hasSize(3);
        assertThat(md.filename()).isEqualTo("evidence-run-1.md");
        assertThat(md.mediaType()).isEqualTo("text/markdown");
        verifyNoInteractions(embabel);
    }

    @Test
    void exportsARunAsPdf() throws Exception {
        var pdf = service.exportRun(project, "run-1", EvidenceExportFormat.PDF, false);

        assertThat(pdf.filename()).isEqualTo("evidence-run-1.pdf");
        assertThat(pdf.mediaType()).isEqualTo("application/pdf");
        try (var doc = org.apache.pdfbox.Loader.loadPDF(pdf.content())) {
            assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(doc)).contains("run-1", "Pushed");
        }
    }

    @Test
    void runOfAnotherProjectIs404() {
        when(evidenceService.getRun(project, "other")).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.exportRun(project, "other", EvidenceExportFormat.CSV, false))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void includeOutputsResolvesReferencedOutputsAndCapsThem() {
        when(embabel.getAgentRunOutputs("run-1")).thenReturn(List.of(
                new AgentRunOutputDto("impl", 0, "0123456789ABCDEF"),
                new AgentRunOutputDto("unreferenced", 1, "nope")));

        EvidenceExportRun run = service.loadRun(project, "run-1", true);

        assertThat(run.outputs()).hasSize(1);
        assertThat(run.outputs().get(0).agentKey()).isEqualTo("impl");
        assertThat(run.outputs().get(0).output()).isEqualTo("0123456789");
        assertThat(run.outputs().get(0).truncated()).isTrue();
        assertThat(run.outputsUnavailable()).isFalse();
    }

    @Test
    void totalOutputBudgetIsSharedAcrossOutputs() {
        when(evidenceService.getRun(project, "run-2")).thenReturn(new EvidenceRun(details("run-2"), List.of(
                eventWithRef("a"), eventWithRef("b"))));
        when(embabel.getAgentRunOutputs("run-2")).thenReturn(List.of(
                new AgentRunOutputDto("a", 0, "0123456789"), new AgentRunOutputDto("b", 1, "0123456789")));

        var outputs = service.loadRun(project, "run-2", true).outputs();

        assertThat(outputs.get(0).output()).hasSize(10);
        assertThat(outputs.get(1).output()).hasSize(5);
        assertThat(outputs.get(1).truncated()).isTrue();
    }

    @Test
    void unreachableAgentServiceExportsWithoutOutputsAndAMarker() {
        when(embabel.getAgentRunOutputs(anyString())).thenThrow(new IllegalStateException("down"));

        var file = service.exportRun(project, "run-1", EvidenceExportFormat.JSON, true);

        assertThat(new String(file.content(), StandardCharsets.UTF_8)).contains("\"outputsUnavailable\" : true");
    }

    @Test
    void aFailingRunIsIsolatedInMultiRunLoading() {
        when(evidenceService.getRun(project, "bad")).thenThrow(new IllegalStateException("boom"));

        var run = service.loadRunIsolated(project, "bad", false);

        assertThat(run.error()).isEqualTo("Run could not be loaded");
        assertThat(run.events()).isEmpty();
        assertThat(service.loadRunIsolated(project, "run-1", false).error()).isNull();
    }

    @Test
    void truncationNeverSplitsAMultibyteCharacter() {
        assertThat(EvidenceExportService.truncateUtf8("aあb", 2)).isEqualTo("a");
        assertThat(EvidenceExportService.truncateUtf8("aあb", 4)).isEqualTo("aあ");
        assertThat(EvidenceExportService.truncateUtf8("abc", 10)).isEqualTo("abc");
    }

    @Test
    void filenamesAreSanitised() {
        assertThat(EvidenceExportService.runFilename("../../etc/passwd\r\nX: y", EvidenceExportFormat.CSV))
                .isEqualTo("evidence-_.._etc_passwd__X__y.csv");
        assertThat(EvidenceExportService.sanitise("")).isEqualTo("run");
        assertThat(EvidenceExportService.sanitise("x".repeat(300))).hasSize(100);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aFailingOutputStreamSurfacesAsUncheckedIoException() {
        OutputStream broken = new OutputStream() {
            @Override public void write(int b) throws IOException { throw new IOException("disk full"); }
            @Override public void write(byte[] b, int off, int len) throws IOException { throw new IOException("disk full"); }
        };
        var model = model(nl.metafactory.aicontrol.model.EvidenceExportScope.PROJECT, run("run-1", event(EvidenceSource.GIT, "x", null)));

        assertThatThrownBy(() -> service.render(model, EvidenceExportFormat.CSV, broken))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void includeOutputsOnARunWithoutOutputReferencesDoesNotCallTheAgentService() {
        when(evidenceService.getRun(project, "run-3")).thenReturn(new EvidenceRun(details("run-3"),
                List.of(event(EvidenceSource.GIT, "Pushed", null))));

        var run = service.loadRun(project, "run-3", true);

        assertThat(run.outputs()).isEmpty();
        assertThat(run.outputsUnavailable()).isFalse();
        verifyNoInteractions(embabel);
    }

    @Test
    void exportWorksWithAJwtUserAndWithAPlainAuthentication() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("sub-1")
                .claim("preferred_username", "alice").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        assertThat(service.exportRun(project, "run-1", EvidenceExportFormat.CSV, false).content()).isNotEmpty();

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("bob", "pw"));
        assertThat(service.exportRun(project, "run-1", EvidenceExportFormat.CSV, false).content()).isNotEmpty();
    }

    private static nl.metafactory.aicontrol.model.EvidenceTimelineEvent eventWithRef(String key) {
        return new nl.metafactory.aicontrol.model.EvidenceTimelineEvent(T0, EvidenceSource.AGENT, key, null, "t", null,
                key, nl.metafactory.aicontrol.model.EvidenceStatus.OK);
    }
}
