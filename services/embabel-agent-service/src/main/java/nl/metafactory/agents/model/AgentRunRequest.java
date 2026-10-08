package nl.metafactory.agents.model;

import nl.metafactory.agents.approval.model.ApprovalGateConfig;

import java.util.List;

public record AgentRunRequest(
        String customerId,
        String specFile,
        List<String> agentIds,
        String requestedBy,
        String repositoryUrl,
        String gitUsername,
        String gitToken,
        ApprovalGateConfig approvalGate,
        // MADP-54: the base branch that agentic branches are cut from and that pull requests
        // target. Resolved once per run at start from the project's defaultBranch
        // (WorkflowStartEnrichmentService); null falls back to openjcockpit.spec-git.base-branch.
        // Like approvalGate, deliberately NOT part of ai-control's AgentRunRequestDto/contract.
        String baseBranch,
        String workflowId,
        String startedBy,
        RunInitiator initiator,
        List<String> chainAncestry
) {}
