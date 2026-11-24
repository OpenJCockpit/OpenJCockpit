package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.generated.api.EvidenceApi;
import nl.metafactory.aicontrol.model.EvidenceExportJob;
import nl.metafactory.aicontrol.model.EvidenceExportJobCreated;
import nl.metafactory.aicontrol.model.EvidenceExportRequest;
import nl.metafactory.aicontrol.model.EvidenceExportFormat;
import nl.metafactory.aicontrol.service.evidenceexport.EvidenceExportService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import nl.metafactory.aicontrol.model.EvidenceOutput;
import nl.metafactory.aicontrol.model.EvidenceRun;
import nl.metafactory.aicontrol.model.EvidenceRunPage;
import nl.metafactory.aicontrol.service.EvidenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class EvidenceController implements EvidenceApi {

    private final EvidenceService evidenceService;
    private final EvidenceExportService exportService;

    public EvidenceController(EvidenceService evidenceService, EvidenceExportService exportService) {
        this.evidenceService = evidenceService;
        this.exportService = exportService;
    }

    @Override
    public ResponseEntity<EvidenceRunPage> listEvidenceRuns(UUID id, Integer limit, Integer offset) {
        return ResponseEntity.ok(evidenceService.listRuns(id, limit != null ? limit : 20, offset != null ? offset : 0));
    }

    @Override
    public ResponseEntity<EvidenceRun> getEvidenceRun(UUID id, String runId) {
        return ResponseEntity.ok(evidenceService.getRun(id, runId));
    }

    @Override
    public ResponseEntity<EvidenceRun> getLatestEvidenceRun(UUID id) {
        return ResponseEntity.ok(evidenceService.getLatestRun(id));
    }

    @Override
    public ResponseEntity<EvidenceOutput> getEvidenceRunOutput(UUID id, String runId, String agentKey) {
        return ResponseEntity.ok(evidenceService.getOutput(id, runId, agentKey));
    }

    @Override
    public ResponseEntity<Resource> exportEvidenceRun(UUID id, String runId, EvidenceExportFormat format,
                                                      Boolean includeOutputs) {
        var file = exportService.exportRun(id, runId, format, Boolean.TRUE.equals(includeOutputs));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename()).build().toString())
                .body(new ByteArrayResource(file.content()));
    }

    // The export job API is part of the contract from phase 1a; its implementation follows in phase 1b.

    @Override
    public ResponseEntity<EvidenceExportJobCreated> createEvidenceExport(UUID id, EvidenceExportRequest request) {
        throw notImplemented();
    }

    @Override
    public ResponseEntity<EvidenceExportJob> getEvidenceExport(UUID id, UUID jobId) {
        throw notImplemented();
    }

    @Override
    public ResponseEntity<Void> cancelEvidenceExport(UUID id, UUID jobId) {
        throw notImplemented();
    }

    @Override
    public ResponseEntity<Resource> downloadEvidenceExport(UUID id, UUID jobId) {
        throw notImplemented();
    }

    private static ResponseStatusException notImplemented() {
        return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Export jobs are not available yet");
    }
}
