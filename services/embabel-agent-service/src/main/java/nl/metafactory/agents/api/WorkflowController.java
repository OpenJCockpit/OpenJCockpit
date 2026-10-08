package nl.metafactory.agents.api;

import nl.metafactory.agents.model.RunInitiator;
import nl.metafactory.agents.policy.PolicyDecisionLogExportService;
import nl.metafactory.agents.policy.model.DecisionLogFilter;
import nl.metafactory.agents.policy.model.PolicyDecisionAuditEntry;
import nl.metafactory.agents.workflow.WorkflowDefinitionValidator;
import nl.metafactory.agents.workflow.WorkflowDefinitionRepository;
import nl.metafactory.agents.workflow.WorkflowExecutionService;
import nl.metafactory.agents.workflow.WorkflowGroupRepository;
import nl.metafactory.agents.workflow.WorkflowLastExecutionService;
import nl.metafactory.agents.workflow.WorkflowOrbValidator;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import nl.metafactory.agents.workflow.model.WorkflowExportBundle;
import nl.metafactory.agents.workflow.model.WorkflowGroup;
import nl.metafactory.agents.workflow.model.WorkflowImportResult;
import nl.metafactory.agents.workflow.model.WorkflowStartInput;
import nl.metafactory.agents.workflow.model.WorkflowStartResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowDefinitionRepository repository;
    private final WorkflowGroupRepository groupRepository;
    private final WorkflowExecutionService executionService;
    private final PolicyDecisionLogExportService decisionLogExportService;
    private final WorkflowDefinitionValidator validator;
    private final WorkflowOrbValidator orbValidator;
    private final WorkflowLastExecutionService lastExecutionService;

    public WorkflowController(WorkflowDefinitionRepository repository, WorkflowGroupRepository groupRepository,
                               WorkflowExecutionService executionService,
                               PolicyDecisionLogExportService decisionLogExportService,
                               WorkflowDefinitionValidator validator,
                               WorkflowOrbValidator orbValidator,
                               WorkflowLastExecutionService lastExecutionService) {
        this.repository = repository;
        this.groupRepository = groupRepository;
        this.executionService = executionService;
        this.decisionLogExportService = decisionLogExportService;
        this.validator = validator;
        this.orbValidator = orbValidator;
        this.lastExecutionService = lastExecutionService;
    }

    @GetMapping
    public List<WorkflowDefinition> list() {
        return lastExecutionService.withLastExecution(repository.findAll());
    }

    /** Exports all workflows and groups as a single JSON bundle. */
    @GetMapping("/export")
    public WorkflowExportBundle export() {
        return new WorkflowExportBundle(groupRepository.findAll(), repository.findAll());
    }

    /**
     * Imports a previously exported JSON bundle. Records are upserted by id;
     * records without an id get a generated id.
     *
     * <p>workflow-trigger-workflow-orb architecture §5.2/AC-51: structural rules (group-or-project,
     * at-least-one-agent, approval-gate placement) still reject the whole import before anything is
     * persisted, exactly as before this feature. Workflow-orb reference rules are checked
     * separately, AFTER every workflow in the bundle has been persisted — a bundle may contain
     * forward references between its own members that only resolve once the whole bundle exists in
     * the repository. Any orb reference that remains unresolved even then (present in neither the
     * bundle nor the pre-existing repository) is reported in {@link WorkflowImportResult#violations()}
     * rather than rejecting the import.
     */
    @PostMapping("/import")
    public WorkflowImportResult importBundle(@RequestBody WorkflowExportBundle bundle) {
        List<WorkflowGroup> groups = bundle.groups() != null ? bundle.groups() : List.of();
        List<WorkflowDefinition> workflows = bundle.workflows() != null ? bundle.workflows() : List.of();
        workflows.forEach(validator::validateStructuralRulesForImport);
        groups.forEach(groupRepository::save);
        workflows.forEach(repository::save);
        List<String> violations = new ArrayList<>();
        for (WorkflowDefinition workflow : workflows) {
            violations.addAll(orbValidator.orbViolations(workflow));
        }
        return new WorkflowImportResult(groups.size(), workflows.size(), violations);
    }

    @GetMapping("/{id}")
    public WorkflowDefinition get(@PathVariable String id) {
        WorkflowDefinition definition = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found: " + id));
        return lastExecutionService.withLastExecution(definition);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowDefinition create(@RequestBody WorkflowDefinition definition) {
        validator.validateForWrite(definition);
        return repository.save(definition);
    }

    @PutMapping("/{id}")
    public WorkflowDefinition update(@PathVariable String id, @RequestBody WorkflowDefinition definition) {
        validator.validateForWrite(definition);
        repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found: " + id));
        return repository.save(definition);
    }

    /**
     * workflow-trigger-workflow-orb architecture §5.2/AC-08: refuses the delete when another
     * workflow still references {@code id} via a workflow orb, naming the referrer(s), instead of
     * silently leaving those orbs dangling.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        List<String> referencedBy = orbValidator.referencedBy(id);
        if (!referencedBy.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete workflow '" + id + "': it is still referenced by workflow orb(s) in: " + referencedBy);
        }
        repository.deleteById(id);
    }

    /**
     * workflow-trigger-workflow-orb architecture §6.2/AC-45: identity always comes from the
     * caller's JWT — never a request field — mirroring the existing
     * {@code preferred_username}/{@code sub} pattern in {@code ApprovalGateController}. A
     * client-supplied actor would be trivially spoofable.
     */
    @PostMapping("/{id}/start")
    public WorkflowStartResponse start(@PathVariable String id,
                                       @RequestBody(required = false) WorkflowStartInput input) {
        RunInitiator initiator = RunInitiator.human(currentUsername(), currentSubject());
        return executionService.startWorkflow(id, input != null ? input : WorkflowStartInput.empty(), initiator);
    }

    @GetMapping("/{id}/decision-logs")
    public List<PolicyDecisionAuditEntry> decisionLogs(
            @PathVariable String id,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String subagentId,
            @RequestParam(required = false) String skillId,
            @RequestParam(required = false) String mcpToolName,
            @RequestParam(required = false) String result) {
        var filter = new DecisionLogFilter(from, to, agentId, subagentId, skillId, mcpToolName, result);
        return decisionLogExportService.exportDecisionLogsForWorkflow(id, filter);
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getPrincipal() instanceof Jwt jwt)
                ? jwt.getClaimAsString("preferred_username")
                : "unknown";
    }

    private String currentSubject() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getPrincipal() instanceof Jwt jwt)
                ? jwt.getClaimAsString("sub")
                : "unknown";
    }
}
