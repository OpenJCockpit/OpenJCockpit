package nl.metafactory.agentruntime.definition.runtime;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.spi.loop.ToolNotFoundAction;
import com.embabel.agent.spi.loop.ToolNotFoundException;
import com.embabel.agent.spi.loop.ToolNotFoundPolicy;

import java.util.List;

/**
 * Replaces Embabel's default {@code AutoCorrectionPolicy}, which maps an unknown tool name to the
 * most similar available tool. For configuration-driven agents a hallucinated or injected tool name
 * must abort, never be rewritten into a real call.
 */
final class StrictToolNotFoundPolicy implements ToolNotFoundPolicy {

    @Override
    public ToolNotFoundAction handle(String requestedTool, List<? extends Tool> availableTools) {
        return new ToolNotFoundAction.Throw(new ToolNotFoundException(requestedTool,
                availableTools.stream().map(t -> t.getDefinition().getName()).toList()));
    }
}
