package nl.metafactory.agentruntime.definition.model;

/** Why a definition could not be resolved into a runnable snapshot. */
public enum ResolutionFailure {
    NOT_FOUND,
    UNAVAILABLE,
    UNAUTHORIZED,
    INVALID,
    UNSUPPORTED,
    TENANT_FORBIDDEN,
    SKILL_MISSING,
    TOOL_DENIED,
    GUARDRAIL_MISSING,
    POLICY_DENIED
}
