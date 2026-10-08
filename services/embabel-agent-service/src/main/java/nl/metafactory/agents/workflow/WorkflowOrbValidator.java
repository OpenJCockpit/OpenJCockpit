package nl.metafactory.agents.workflow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import nl.metafactory.agents.config.WorkflowTriggerProperties;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import nl.metafactory.agents.workflow.model.WorkflowGroup;
import nl.metafactory.agents.workflow.model.WorkflowOrb;

/**
 * Validates workflow-orb references at save time.
 *
 * <p>{@link #validateForWrite(WorkflowDefinition)} throws on the first violation and is
 * intended for create/update endpoints. {@link #orbViolations(WorkflowDefinition)} collects
 * every violation without throwing and is intended for import reporting and delete-guard
 * listing. {@link #referencedBy(String)} lists the workflows that reference a given workflow
 * via an orb.
 *
 * <p>The cycle check performed here is a best-effort heuristic bounded by a budget derived
 * from the configured maximum orbs per workflow and maximum chain depth. It is not the
 * authoritative cycle guard: the authoritative check is the {@code WorkflowChainResolver}
 * check performed at each child start.
 */
@Component
public class WorkflowOrbValidator {

    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final WorkflowGroupRepository workflowGroupRepository;
    private final WorkflowTriggerProperties workflowTriggerProperties;

    public WorkflowOrbValidator(WorkflowDefinitionRepository workflowDefinitionRepository,
                                WorkflowGroupRepository workflowGroupRepository,
                                WorkflowTriggerProperties workflowTriggerProperties) {
        this.workflowDefinitionRepository = workflowDefinitionRepository;
        this.workflowGroupRepository = workflowGroupRepository;
        this.workflowTriggerProperties = workflowTriggerProperties;
    }

    /**
     * Validates the workflow-orb references of {@code d} and throws a
     * {@link ResponseStatusException} with {@link HttpStatus#BAD_REQUEST} on the first
     * violation found. Intended for create/update endpoints.
     */
    public void validateForWrite(WorkflowDefinition d) {
        List<String> violations = orbViolations(d);
        if (!violations.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, violations.get(0));
        }
    }

    /**
     * Evaluates every workflow-orb validation rule against {@code d} and returns every
     * violation message found (empty list if none). Intended for import reporting and
     * delete-guard listing.
     *
     * <p>Returns immediately, without touching either repository, when {@code d} has no orbs
     * at all — the overwhelmingly common case — so that a caller may safely construct this
     * validator with test doubles that only support the orb-bearing scenarios they actually
     * exercise.
     */
    public List<String> orbViolations(WorkflowDefinition d) {
        List<String> violations = new ArrayList<>();
        List<WorkflowOrb> orbs = d.workflowOrbs() == null ? List.of() : d.workflowOrbs();

        if (orbs.isEmpty()) {
            return violations;
        }

        // Rule 1: Mode present (per orb).
        for (WorkflowOrb orb : orbs) {
            if (orb.mode() == null) {
                violations.add("Workflow orb targeting '" + orb.workflowId() + "' in workflow '" + d.id()
                        + "' must specify a mode (SEQUENTIAL or PARALLEL)");
            }
        }

        // Rule 2: Cap (whole list, checked once).
        if (orbs.size() > workflowTriggerProperties.getMaxOrbsPerWorkflow()) {
            violations.add("Workflow '" + d.id() + "' has " + orbs.size()
                    + " orb(s), exceeding the configured maximum of " + workflowTriggerProperties.getMaxOrbsPerWorkflow());
        }

        // Rule 3: Self-reference (per orb).
        for (WorkflowOrb orb : orbs) {
            if (orb.workflowId() != null && orb.workflowId().equals(d.id())) {
                violations.add("Workflow '" + d.id() + "' cannot reference itself via a workflow orb");
            }
        }

        // Rule 4: Unknown target (per orb).
        for (WorkflowOrb orb : orbs) {
            if (orb.workflowId() != null && workflowDefinitionRepository.findById(orb.workflowId()).isEmpty()) {
                violations.add("Workflow orb in '" + d.id() + "' references unknown workflow '" + orb.workflowId() + "'");
            }
        }

        // Rule 5: Anchor membership (per orb).
        List<String> agentIds = d.agentIds() == null ? List.of() : d.agentIds();
        for (WorkflowOrb orb : orbs) {
            if (orb.placementStage() != null && !orb.placementStage().isBlank() && !agentIds.contains(orb.placementStage())) {
                violations.add("Workflow orb placement stage '" + orb.placementStage()
                        + "' is not among this workflow's selected agents: " + agentIds);
            }
        }

        // Rule 6: Scope (per orb whose target resolves under rule 4), computed lazily so
        // effectiveProject(d) — which may consult workflowGroupRepository — is only invoked
        // when at least one orb's target actually resolves.
        String parentProject = null;
        for (WorkflowOrb orb : orbs) {
            if (orb.workflowId() == null) {
                continue;
            }
            var targetOpt = workflowDefinitionRepository.findById(orb.workflowId());
            if (targetOpt.isEmpty()) {
                continue;
            }
            if (parentProject == null) {
                parentProject = effectiveProject(d);
            }
            WorkflowDefinition target = targetOpt.get();
            String targetProject = effectiveProject(target);
            if (parentProject.isBlank() && !targetProject.isBlank()) {
                violations.add("Global workflow '" + d.id() + "' may only reference other global workflows, but orb targets project-scoped workflow '" + target.id() + "' (project '" + targetProject + "')");
            } else if (!parentProject.isBlank() && !targetProject.isBlank() && !targetProject.equals(parentProject)) {
                violations.add("Workflow '" + d.id() + "' (project '" + parentProject + "') may not reference workflow '" + target.id() + "' (project '" + targetProject + "') — cross-project orb references are not allowed");
            }
        }

        // Rule 7: Cycle (whole list, checked once, best-effort).
        String cycleViolation = detectCycle(d);
        if (cycleViolation != null) {
            violations.add(cycleViolation);
        }

        return violations;
    }

    /**
     * Returns the {@code id()} of every stored workflow (other than {@code workflowId})
     * that references {@code workflowId} via at least one workflow orb.
     */
    public List<String> referencedBy(String workflowId) {
        List<String> result = new ArrayList<>();
        for (WorkflowDefinition def : workflowDefinitionRepository.findAll()) {
            if (def.id().equals(workflowId)) {
                continue;
            }
            List<WorkflowOrb> orbs = def.workflowOrbs() == null ? List.of() : def.workflowOrbs();
            for (WorkflowOrb orb : orbs) {
                if (orb.workflowId() != null && orb.workflowId().equals(workflowId)) {
                    result.add(def.id());
                    break;
                }
            }
        }
        return result;
    }

    private String effectiveProject(WorkflowDefinition d) {
        if (d.projectName() != null && !d.projectName().isBlank()) {
            return d.projectName();
        }
        if (d.groupId() != null && !d.groupId().isBlank()) {
            WorkflowGroup group = workflowGroupRepository.findById(d.groupId()).orElse(null);
            if (group != null && group.projectName() != null && !group.projectName().isBlank()) {
                return group.projectName();
            }
        }
        return "";
    }

    private String detectCycle(WorkflowDefinition d) {
        Map<String, List<String>> edges = new HashMap<>();
        for (WorkflowDefinition def : workflowDefinitionRepository.findAll()) {
            List<String> targets = new ArrayList<>();
            List<WorkflowOrb> orbs = def.workflowOrbs() == null ? List.of() : def.workflowOrbs();
            for (WorkflowOrb orb : orbs) {
                if (orb.workflowId() != null) {
                    targets.add(orb.workflowId());
                }
            }
            edges.put(def.id(), targets);
        }
        List<String> dTargets = new ArrayList<>();
        List<WorkflowOrb> dOrbs = d.workflowOrbs() == null ? List.of() : d.workflowOrbs();
        for (WorkflowOrb orb : dOrbs) {
            if (orb.workflowId() != null) {
                dTargets.add(orb.workflowId());
            }
        }
        edges.put(d.id(), dTargets);

        int budget = workflowTriggerProperties.getMaxOrbsPerWorkflow() * workflowTriggerProperties.getMaxChainDepth();
        Set<String> visited = new LinkedHashSet<>();
        visited.add(d.id());
        int edgesFollowed = 0;

        Deque<List<String>> stack = new ArrayDeque<>();
        stack.push(new ArrayList<>(List.of(d.id())));
        while (!stack.isEmpty() && edgesFollowed < budget) {
            List<String> currentPath = stack.pop();
            String current = currentPath.get(currentPath.size() - 1);
            List<String> nexts = edges.getOrDefault(current, List.of());
            for (String next : nexts) {
                if (edgesFollowed >= budget) {
                    break;
                }
                if (next.equals(d.id())) {
                    return "Workflow orb cycle detected: " + String.join(" -> ", currentPath) + " -> " + d.id();
                }
                if (!visited.contains(next)) {
                    visited.add(next);
                    edgesFollowed++;
                    List<String> nextPath = new ArrayList<>(currentPath);
                    nextPath.add(next);
                    stack.push(nextPath);
                }
            }
        }
        return null;
    }
}
