package nl.metafactory.agents.definition.tool;

import com.embabel.agent.api.tool.Tool;
import nl.metafactory.agents.definition.markdown.Identifiers;

import java.util.Objects;

/**
 * A server-side executable tool, registered in code as a Spring bean. Definitions can only refer
 * to these by {@code id}; nothing in a Markdown or skill file can create one.
 *
 * <p>The id may contain dots ({@code customer.read}); the name the LLM sees is the id with every
 * non {@code [a-z0-9_-]} character replaced by {@code _}, because OpenAI-compatible function names
 * do not allow dots.
 */
public record RegisteredAgentTool(String id, Tool tool) {

    public RegisteredAgentTool {
        if (id == null || id.length() > Identifiers.MAX_LENGTH || !Identifiers.QUALIFIED.matcher(id).matches()) {
            throw new IllegalArgumentException("invalid tool id");
        }
        Objects.requireNonNull(tool, "tool");
        if (!tool.getDefinition().getName().equals(llmName(id))) {
            throw new IllegalArgumentException("tool " + id + " must be named " + llmName(id) + " for the LLM");
        }
    }

    public static String llmName(String id) {
        return id.replaceAll("[^a-z0-9_-]", "_");
    }
}
