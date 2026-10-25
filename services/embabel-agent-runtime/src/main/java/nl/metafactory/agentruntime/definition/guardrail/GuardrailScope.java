package nl.metafactory.agentruntime.definition.guardrail;

/** Traffic a guardrail executor actually inspects. */
public enum GuardrailScope {
    LLM_INPUT,
    LLM_OUTPUT,
    TOOL_CALL
}
