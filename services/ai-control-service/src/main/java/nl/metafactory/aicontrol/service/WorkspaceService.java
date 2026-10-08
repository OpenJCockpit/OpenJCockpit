package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.client.AgentRunDto;
import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.model.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class WorkspaceService {

    private static final String SELECTED_RUN_ID_KEY = "pricing-rules-run";

    private static final Map<String, String> AGENT_STATUS_TO_KIND = Map.of(
        "OK", "done",
        "RUNNING", "active",
        "WAITING", "waiting",
        "FAILED", "changes"
    );

    private static final List<String> AGENT_ORDER =
        List.of("requirement", "impact", "test-design", "implementation", "review", "evidence");

    private static final Map<String, String> AGENT_LABELS = Map.of(
        "requirement", "Requirement Agent",
        "impact", "Impact Analysis Agent",
        "test-design", "Test Design Agent",
        "implementation", "Implementation Agent",
        "review", "Review Agent",
        "evidence", "Evidence Agent"
    );

    private static final Map<String, String> AGENT_DESCRIPTIONS = Map.of(
        "requirement", "Retrieve and validate requirements",
        "impact", "Perform impact analysis",
        "test-design", "Design test scenarios",
        "implementation", "Generate & modify code",
        "review", "Code review & quality check",
        "evidence", "Collect & log evidence"
    );

    private final SpecRepositoryClient specRepositoryClient;
    private final EmbabelAgentClient embabelAgentClient;

    public WorkspaceService(SpecRepositoryClient specRepositoryClient,
                            EmbabelAgentClient embabelAgentClient) {
        this.specRepositoryClient = specRepositoryClient;
        this.embabelAgentClient = embabelAgentClient;
    }

    public Workspace loadWorkspace(String customerId) {
        var specs = specRepositoryClient.listSpecs(customerId);
        var agents = resolveAgentCards();

        return new Workspace(
            new CustomerSummary(customerId, "Noordzee Logistics B.V.", "Logistics & Transport",
                                "Production", "today 13:24", "Internal", "eu-west-1"),
            "pricing-rules.spec.md",
            specs,
            agents,
            pullRequests(),
            evidenceEvents(),
            new EvidenceDetails("v1.3.2", "run_2025-05-06_1303",
                                "RQ → IA → TD → IMP → RV → EV", "13:24 today"),
            qualityControls(),
            List.of(
                new StackServiceStatus("Embabel Orchestrator", "Orchestration of AI agents", "live"),
                new StackServiceStatus("Spring AI Control Service", "Observability & Governance", "live")
            ),
            new FooterStatus("Production", "eu-west-1", "Internal", "2.4.1", "1.8.3")
        );
    }

    private List<AgentCard> resolveAgentCards() {
        Optional<AgentRunDto> run = embabelAgentClient.getLatestRun(SELECTED_RUN_ID_KEY);
        return run.map(this::mapToAgentCards).orElse(defaultAgentCards());
    }

    List<AgentCard> mapToAgentCards(AgentRunDto run) {
        return AGENT_ORDER.stream().map(agentId -> {
            var latestEvent = run.events().stream()
                .filter(e -> agentId.equals(e.agentId()))
                .reduce((first, second) -> second)
                .orElse(null);

            String status;
            String statusKind;
            if (latestEvent == null) {
                status = "Waiting";
                statusKind = "waiting";
            } else {
                status = latestEvent.title();
                statusKind = AGENT_STATUS_TO_KIND.getOrDefault(latestEvent.status(), "waiting");
            }
            return new AgentCard(agentId, agentLabel(agentId), agentDescription(agentId), status, statusKind);
        }).toList();
    }

    private List<AgentCard> defaultAgentCards() {
        return List.of(
            new AgentCard("requirement", "Requirement Agent", "Retrieve and validate requirements", "Active", "active"),
            new AgentCard("impact", "Impact Analysis Agent", "Perform impact analysis", "Active", "active"),
            new AgentCard("test-design", "Test Design Agent", "Design test scenarios", "Active", "active"),
            new AgentCard("implementation", "Implementation Agent", "Generate & modify code", "Active", "active"),
            new AgentCard("review", "Review Agent", "Code review & quality check", "Done", "done"),
            new AgentCard("evidence", "Evidence Agent", "Collect & log evidence", "Awaiting approval", "waiting")
        );
    }

    private String agentLabel(String agentId) {
        return AGENT_LABELS.getOrDefault(agentId, agentId);
    }

    private String agentDescription(String agentId) {
        return AGENT_DESCRIPTIONS.getOrDefault(agentId, agentId);
    }

    private List<PullRequestSummary> pullRequests() {
        return List.of(
            new PullRequestSummary("482", "pricing-rules update", "feature/pricing-rules-update",
                                   "13:02 today", "Author", "Jan de Vries", "Open", "open"),
            new PullRequestSummary("487", "add approval validation", "feature/approval-validation",
                                   "11:47 today", "Reviewer", "Maria Bakker", "In review", "review"),
            new PullRequestSummary("491", "refine customer profile mapping", "feature/customer-profile-mapping",
                                   "10:35 today", "Reviewer", "Tom Janssen", "Changes requested", "changes"),
            new PullRequestSummary("476", "update pricing engine", "feature/pricing-engine",
                                   "Yesterday 16:22", "Reviewer", "Lisa van Dijk", "In review", "review"),
            new PullRequestSummary("468", "add audit logging", "feature/audit-logging",
                                   "2 May 14:08", "Author", "Pieter Smit", "Open", "open")
        );
    }

    private List<EvidenceEvent> evidenceEvents() {
        return List.of(
            new EvidenceEvent("13:04", "Spec loaded", "Requirement Agent", "ok"),
            new EvidenceEvent("13:07", "Impact analysis recorded", "Impact Analysis Agent", "ok"),
            new EvidenceEvent("13:12", "Test set generated", "Test Design Agent", "ok"),
            new EvidenceEvent("13:16", "Implementation proposal saved", "Implementation Agent", "ok"),
            new EvidenceEvent("13:22", "Human approval logged", "Review Agent", "ok"),
            new EvidenceEvent("13:24", "Evidence data recorded", "Evidence Agent", "ok")
        );
    }

    private List<QualityControl> qualityControls() {
        return List.of(
            new QualityControl("Unit test coverage", "Compliant", "92%"),
            new QualityControl("NIST 2", "Compliant", ""),
            new QualityControl("OWASP ZAP", "Compliant", "No critical issues"),
            new QualityControl("ISO 25010", "Compliant", "8/8 attributes"),
            new QualityControl("SAST", "Compliant", "No high issues"),
            new QualityControl("Dependency scan", "Compliant", "No known vulnerabilities"),
            new QualityControl("Architecture rules", "Compliant", "0 deviations")
        );
    }
}
