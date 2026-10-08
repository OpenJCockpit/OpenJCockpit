package nl.metafactory.agents.subagent;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.common.PromptRunner;
import com.embabel.common.ai.model.LlmOptions;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public abstract class SubagentTestBase {

    @Mock protected Ai ai;
    @Mock protected PromptRunner promptRunner;

    @BeforeEach
    void initMocks() {
        MockitoAnnotations.openMocks(this);
        when(ai.withLlm(any(LlmOptions.class))).thenReturn(promptRunner);
    }

    @SuppressWarnings("unchecked")
    protected <T> void givenAiReturns(Class<T> type, T value) {
        when(promptRunner.createObject(anyString(), any(Class.class))).thenReturn(value);
    }
}
