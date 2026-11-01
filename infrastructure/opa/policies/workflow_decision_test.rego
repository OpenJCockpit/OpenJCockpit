# Unit tests for workflow_decision.rego: `opa test infrastructure/opa/policies -v`
# (CI: .github/workflows/infra-ci.yml; locally: deploy/scripts/validate-policy.sh).
package openjcockpit.workflow

import future.keywords.every
import future.keywords.in

base := {"workflowId": "wf-1", "customerId": "acme", "workflowExecutionId": "run-1"}

tool(name) := object.union(base, {"action": "mcp.tool.invoke", "mcpToolName": name, "mcpToolOperation": "invoke"})

test_missing_workflow_id_is_denied {
	d := decision with input as {"customerId": "acme", "action": "workflow.start"}
	d.allowed == false
	d.auditTags == ["missing-identifier"]
}

test_missing_customer_id_is_denied {
	d := decision with input as {"workflowId": "wf-1", "customerId": "", "action": "workflow.start"}
	d.allowed == false
}

test_a2a_tool_call_without_workflow_is_denied {
	d := decision with input as {"customerId": "local", "action": "mcp.tool.invoke", "mcpToolName": "git.read_file"}
	d.allowed == false
}

test_valid_workflow_start_is_allowed {
	d := decision with input as object.union(base, {"action": "workflow.start"})
	d.allowed == true
	d.riskLevel == "low"
}

test_agent_git_tools_are_allowed {
	every name in ["git.list_files", "git.read_file", "git.write_file", "git_commit", "git_push", "git_create_branch"] {
		d := decision with input as tool(name)
		d.allowed == true
	}
}

test_destructive_git_operations_are_denied {
	every name in ["git_force_push", "git.reset_hard", "git_rebase", "git_delete_branch", "git.delete_repository"] {
		d := decision with input as tool(name)
		d.allowed == false
		d.riskLevel == "critical"
		"forbidden-operation" in d.auditTags
	}
}

test_forbidden_operation_field_is_denied {
	d := decision with input as object.union(tool("git_push"), {"mcpToolOperation": "FORCE_PUSH"})
	d.allowed == false
}

test_unknown_tool_families_are_denied {
	every name in ["shell.exec", "fs.delete", "http.fetch", "kubectl"] {
		d := decision with input as tool(name)
		d.allowed == false
	}
}

test_tool_call_without_tool_name_is_denied {
	d := decision with input as object.union(base, {"action": "mcp.tool.invoke"})
	d.allowed == false
	"unknown-tool" in d.auditTags
}

test_restricted_project_requires_human_approval {
	d := decision with input as object.union(base, {"action": "workflow.start", "projectClassification": "restricted"})
	d.allowed == true
	d.requiredApproval == true
	d.riskLevel == "high"
}

test_restricted_project_with_approval_is_allowed_without_further_approval {
	d := decision with input as object.union(base, {"action": "workflow.start", "projectClassification": "restricted", "humanApproval": true})
	d.allowed == true
	d.requiredApproval == false
}

test_non_tool_actions_are_not_subject_to_tool_guardrails {
	d := decision with input as object.union(base, {"action": "agent.use", "agentId": "software-developer"})
	d.allowed == true
}
