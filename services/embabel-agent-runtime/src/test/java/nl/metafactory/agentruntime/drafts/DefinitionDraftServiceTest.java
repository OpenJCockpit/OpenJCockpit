package nl.metafactory.agentruntime.drafts;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.common.PromptRunner;
import com.embabel.common.ai.model.LlmOptions;
import nl.metafactory.common.agentruntime.DefinitionDrafts;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Each draft kind is one structured-output call of the default LLM with the concept in the prompt. */
class DefinitionDraftServiceTest {

    private final Ai ai = mock(Ai.class);
    private final PromptRunner runner = mock(PromptRunner.class);
    private final DefinitionDraftService service = new DefinitionDraftService(ai);

    @Test
    void draftsEveryKindWithTheConceptAndItsOwnSchema() {
        when(ai.withLlm(any(LlmOptions.class))).thenReturn(runner);
        var agent = new DefinitionDrafts.AgentDraft("a", null, null, null, List.of(), List.of(), List.of(), null, false);
        var subagent = new DefinitionDrafts.SubagentDraft("s", null, null, null, null, List.of(), List.of(), null);
        var skill = new DefinitionDrafts.SkillDraft("k", null, null, null, null, List.of(), null);
        when(runner.createObject(any(String.class), eq(DefinitionDrafts.AgentDraft.class))).thenReturn(agent);
        when(runner.createObject(any(String.class), eq(DefinitionDrafts.SubagentDraft.class))).thenReturn(subagent);
        when(runner.createObject(any(String.class), eq(DefinitionDrafts.SkillDraft.class))).thenReturn(skill);

        assertThat(service.draft(DefinitionDrafts.Kind.AGENT, "triage issues")).isSameAs(agent);
        assertThat(service.draft(DefinitionDrafts.Kind.SUBAGENT, "route issues")).isSameAs(subagent);
        assertThat(service.draft(DefinitionDrafts.Kind.SKILL, "classify")).isSameAs(skill);

        var prompt = ArgumentCaptor.forClass(String.class);
        verify(runner).createObject(prompt.capture(), eq(DefinitionDrafts.AgentDraft.class));
        assertThat(prompt.getValue()).contains("structured agent definition", "Concept: triage issues");
    }
}
