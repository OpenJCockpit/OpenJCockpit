package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.generated.api.EvidenceApi;
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

    public EvidenceController(EvidenceService evidenceService) {
        this.evidenceService = evidenceService;
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
}
