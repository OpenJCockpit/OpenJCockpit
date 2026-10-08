# Default policy for openjcockpit.opa.policy-path (/v1/data/openjcockpit/workflow/decision).
#
# This is a starting point, not final governance logic — replace/extend the rules below with
# your organization's real approval and risk requirements. Every field in each `result` object
# below maps 1:1 onto embabel-agent-service's OpaDecisionResponse.OpaResult (see
# nl.metafactory.agents.policy.model.OpaDecisionResponse and PolicyDecisionContext for the full
# shape of `input`).
package openjcockpit.workflow

# Rules are evaluated top to bottom; the first matching branch wins.
decision = result {
	missing_identifier
	result := {
		"allowed": false,
		"reason": "workflowId and customerId are required for a policy decision.",
		"requiredApproval": false,
		"riskLevel": "medium",
		"auditTags": ["missing-identifier"],
	}
} else = result {
	input.projectClassification == "restricted"
	not input.humanApproval
	result := {
		"allowed": true,
		"reason": "Restricted project changes require human approval before execution.",
		"requiredApproval": true,
		"riskLevel": "high",
		"auditTags": ["restricted-project"],
	}
} else = result {
	result := {
		"allowed": true,
		"reason": "No matching policy rule; default allow.",
		"requiredApproval": false,
		"riskLevel": "low",
		"auditTags": [],
	}
}

missing_identifier {
	not input.workflowId
}

missing_identifier {
	input.workflowId == null
}

missing_identifier {
	input.workflowId == ""
}

missing_identifier {
	not input.customerId
}

missing_identifier {
	input.customerId == null
}

missing_identifier {
	input.customerId == ""
}
