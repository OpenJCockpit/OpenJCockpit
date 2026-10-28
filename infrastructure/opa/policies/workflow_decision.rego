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
	forbidden_tool
	result := {
		"allowed": false,
		"reason": sprintf("Tool operation '%v' is forbidden by policy.", [tool_label]),
		"requiredApproval": false,
		"riskLevel": "critical",
		"auditTags": ["guardrail", "forbidden-operation"],
	}
} else = result {
	unknown_tool
	result := {
		"allowed": false,
		"reason": sprintf("Tool '%v' is not a known tool family; tools are denied by default.", [tool_label]),
		"requiredApproval": false,
		"riskLevel": "high",
		"auditTags": ["guardrail", "unknown-tool"],
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

# ── Tool guardrails ──────────────────────────────────────────────────────────────────────────────
# Agents' tools (embabel-agent-runtime) are `git.<op>`; the MCP gateway uses `git_<op>`. Anything
# else is unknown and denied; destructive or history-rewriting operations are denied explicitly.

tool_call {
	input.action == "mcp.tool.invoke"
}

known_tool_prefixes := {"git.", "git_"}

# Never undefined: a reason built from an undefined value would make a deny branch fall through to allow.
default tool_label := "<none>"

tool_label := input.mcpToolName {
	is_string(input.mcpToolName)
}

forbidden_tool_operations := {
	"force_push", "push_force", "reset_hard", "reset", "rebase", "delete_branch",
	"delete_repository", "exec", "shell", "run_command",
}

tool_operation := op {
	startswith(input.mcpToolName, "git.")
	op := substring(input.mcpToolName, 4, -1)
} else := op {
	startswith(input.mcpToolName, "git_")
	op := substring(input.mcpToolName, 4, -1)
} else := lower(input.mcpToolName)

forbidden_tool {
	tool_call
	forbidden_tool_operations[tool_operation]
}

forbidden_tool {
	tool_call
	lower(input.mcpToolOperation) == forbidden_tool_operations[_]
}

unknown_tool {
	tool_call
	not input.mcpToolName
}

unknown_tool {
	tool_call
	is_string(input.mcpToolName)
	not has_known_prefix(input.mcpToolName)
}

has_known_prefix(name) {
	startswith(name, known_tool_prefixes[_])
}
