package nl.metafactory.agents.agent;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.common.PromptRunner;
import com.embabel.common.ai.model.LlmOptions;
import nl.metafactory.agents.domain.ImpactReport;
import nl.metafactory.agents.domain.RequirementAnalysis;
import nl.metafactory.agents.domain.SpecContent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImpactAnalysisAgentTest {

    @Test
    void analyzeImpactReturnsImpactReport() {
        var ai = mock(Ai.class);
        var promptRunner = mock(PromptRunner.class);
        when(ai.withLlm(any(LlmOptions.class))).thenReturn(promptRunner);
        var expected = new ImpactReport("id", List.of("module-a"), "LOW");
        when(promptRunner.createObject(anyString(), any())).thenReturn(expected);

        var agent = new ImpactAnalysisAgent(ai);
        var result = agent.analyzeImpact(
            new SpecContent("id", "file.md", "content", ""),
            new RequirementAnalysis("id", List.of("req1"), "summary")
        );

        assertThat(result).isEqualTo(expected);
    }
}
