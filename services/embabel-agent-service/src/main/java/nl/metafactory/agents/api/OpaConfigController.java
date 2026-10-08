package nl.metafactory.agents.api;

import nl.metafactory.agents.policy.OpenPolicyAgentClient;
import nl.metafactory.agents.policy.config.OpaProperties;
import nl.metafactory.agents.policy.model.FailMode;
import nl.metafactory.agents.policy.model.OpaConfigDto;
import nl.metafactory.agents.policy.model.OpaConfigRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * OpaProperties is a single global, mutable properties bean (see its javadoc) — PUT here updates
 * it at runtime; edits revert to the env-var defaults on service restart.
 */
@RestController
@RequestMapping("/api/opa-config")
public class OpaConfigController {

    private final OpaProperties properties;
    private final OpenPolicyAgentClient client;

    public OpaConfigController(OpaProperties properties, OpenPolicyAgentClient client) {
        this.properties = properties;
        this.client = client;
    }

    @GetMapping
    public OpaConfigDto get() {
        return toDto();
    }

    @PutMapping
    public OpaConfigDto update(@RequestBody OpaConfigRequest request) {
        properties.setEnabled(request.enabled());
        if (request.baseUrl() != null) {
            properties.setBaseUrl(request.baseUrl());
        }
        if (request.policyPath() != null) {
            properties.setPolicyPath(request.policyPath());
        }
        if (request.healthPath() != null) {
            properties.setHealthPath(request.healthPath());
        }
        if (request.timeoutSeconds() != null) {
            properties.setTimeoutSeconds(request.timeoutSeconds());
        }
        if (request.failMode() != null) {
            properties.setFailMode(FailMode.valueOf(request.failMode()));
        }
        if (request.decisionLoggingEnabled() != null) {
            properties.setDecisionLoggingEnabled(request.decisionLoggingEnabled());
        }
        if (request.decisionLogExportEnabled() != null) {
            properties.setDecisionLogExportEnabled(request.decisionLogExportEnabled());
        }
        if (request.environment() != null) {
            properties.setEnvironment(request.environment());
        }
        if (request.customerLabel() != null) {
            properties.setCustomerLabel(request.customerLabel());
        }
        if (request.projectLabel() != null) {
            properties.setProjectLabel(request.projectLabel());
        }
        if (request.authToken() != null) {
            properties.setAuthToken(request.authToken());
        }
        return toDto();
    }

    @GetMapping("/health")
    public Map<String, Boolean> health() {
        return Map.of("reachable", client.isReachable());
    }

    private OpaConfigDto toDto() {
        return new OpaConfigDto(properties.isEnabled(), properties.getBaseUrl(), properties.getPolicyPath(),
                properties.getHealthPath(), properties.getTimeoutSeconds(), properties.getFailMode().name(),
                properties.isDecisionLoggingEnabled(), properties.isDecisionLogExportEnabled(),
                properties.getEnvironment(), properties.getCustomerLabel(), properties.getProjectLabel(),
                properties.getAuthToken() != null && !properties.getAuthToken().isBlank());
    }
}
