package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.model.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ModelRoutingService {
    public ModelRouteDecision decide(ModelRouteRequest request) {
        var classification = request.dataClassification() == null ? DataClassification.L1_INTERNAL : request.dataClassification();

        return switch (classification) {
            case L0_PUBLIC -> new ModelRouteDecision("CLOUD", "cloud-fast", true,
                    "Public or non-sensitive data: cloud model allowed for speed.",
                    List.of("audit-log", "prompt-template-version"));
            case L1_INTERNAL -> new ModelRouteDecision("HYBRID", "cloud-premium-or-local", true,
                    "Internal data: cloud allowed with enterprise settings; local possible per customer policy.",
                    List.of("audit-log", "tenant-isolation", "no-training-policy"));
            case L2_CONFIDENTIAL -> new ModelRouteDecision("LOCAL", "local-coding", false,
                    "Confidential customer context: local model by default.",
                    List.of("redaction", "local-inference", "human-approval"));
            case L3_PERSONAL -> new ModelRouteDecision("LOCAL_PRIVATE", "local-private", false,
                    "Personal or regulated data: cloud blocked.",
                    List.of("privacy-review", "restricted-logging", "human-approval"));
            case L4_SECRET -> new ModelRouteDecision("BLOCKED", "none", false,
                    "Secrets must never be sent to an LLM, not even a local one.",
                    List.of("secret-manager-only", "manual-remediation"));
        };
    }
}
