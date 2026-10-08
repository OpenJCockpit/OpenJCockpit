package nl.metafactory.aicontrol.model;

import java.util.List;

public record Workspace(
        CustomerSummary customer,
        String selectedSpecFile,
        List<SpecFile> specs,
        List<AgentCard> agents,
        List<PullRequestSummary> pullRequests,
        List<EvidenceEvent> evidenceEvents,
        EvidenceDetails evidenceDetails,
        List<QualityControl> qualityControls,
        List<StackServiceStatus> stack,
        FooterStatus footer
) {}
