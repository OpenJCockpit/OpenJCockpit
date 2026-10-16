package nl.metafactory.agents.definition.tool;

import com.embabel.agent.api.tool.Tool;
import nl.metafactory.agents.definition.runtime.RunContext;

/**
 * A registered tool whose behaviour depends on the run it is called in (e.g. the git workspace a
 * workflow run is bound to). The bound tool hands it the run's {@link RunContext}, which the LLM can
 * neither see nor change; the plain {@link Tool#call(String)} is never used for such a tool.
 */
public interface RunScopedTool extends Tool {

    Tool.Result call(String input, RunContext run);

    @Override
    default Tool.Result call(String input) {
        return Tool.Result.error("this tool is only available inside an agent run");
    }
}
