package nl.metafactory.agents.workflow;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.common.PromptRunner;
import com.embabel.common.ai.model.LlmOptions;
import nl.metafactory.agents.workflow.model.AgentSpec;
import nl.metafactory.agents.workflow.model.SkillSpec;
import nl.metafactory.agents.workflow.model.SubagentSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class PromptToDefinitionServiceTest {

    @Mock private Ai ai;
    @Mock private PromptRunner promptRunner;

    private PromptToDefinitionService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(ai.withLlm(any(LlmOptions.class))).thenReturn(promptRunner);
        service = new PromptToDefinitionService(ai);
    }

    @SuppressWarnings("unchecked")
    private String capturedPrompt(Class<?> targetType) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(promptRunner).createObject(captor.capture(), (Class) any(Class.class));
        return captor.getValue();
    }

    @Test
    void generateAgentSpecSendsConceptToLlmAndSlugifiesReturnedName() {
        var llmDraft = new AgentSpec("Triage Incoming Issues", "Handles triage", "triage-lead",
                "Classify by severity and route to the right team.", List.of("router"), List.of("classifier"), null, null, false);
        when(promptRunner.createObject(anyString(), any(Class.class))).thenReturn(llmDraft);

        var spec = service.generateAgentSpec("Triage incoming support issues by severity");

        assertThat(spec.name()).isEqualTo("triage-incoming-issues");
        assertThat(spec.role()).isEqualTo("triage-lead");
        assertThat(spec.instructions()).isEqualTo("Classify by severity and route to the right team.");
        assertThat(spec.subagentNames()).containsExactly("router");
        assertThat(spec.skillNames()).containsExactly("classifier");
        assertThat(spec.mcpTools()).isEmpty();
        assertThat(capturedPrompt(AgentSpec.class)).contains("Triage incoming support issues by severity");
    }

    @Test
    void generateAgentSpecUsesExampleConceptWhenPromptBlank() {
        var llmDraft = new AgentSpec(null, null, null, null, null, null, null, null, false);
        when(promptRunner.createObject(anyString(), any(Class.class))).thenReturn(llmDraft);

        var spec = service.generateAgentSpec("   ");

        assertThat(capturedPrompt(AgentSpec.class)).contains("generic example agent");
        assertThat(spec.name()).isNotBlank();
        assertThat(spec.role()).isEqualTo("generated");
        assertThat(spec.description()).isNotBlank();
        assertThat(spec.instructions()).contains("generic example agent");
        assertThat(spec.subagentNames()).isEmpty();
        assertThat(spec.skillNames()).isEmpty();
        assertThat(spec.mcpTools()).isEmpty();
    }

    @Test
    void generateAgentSpecUsesExampleConceptWhenPromptNull() {
        var llmDraft = new AgentSpec(null, null, null, null, null, null, null, null, false);
        when(promptRunner.createObject(anyString(), any(Class.class))).thenReturn(llmDraft);

        service.generateAgentSpec(null);

        assertThat(capturedPrompt(AgentSpec.class)).contains("generic example agent");
    }

    @Test
    void generateAgentSpecSlugifiesDeduplicatesAndDropsBlankSubagentAndSkillNames() {
        var llmDraft = new AgentSpec("Reviewer", "desc", "role", "instructions",
                List.of("Security Scanner", "security-scanner", "  ", "Linting Helper!"),
                List.of("Diff Summary", ""), null, null, false);
        when(promptRunner.createObject(anyString(), any(Class.class))).thenReturn(llmDraft);

        var spec = service.generateAgentSpec("concept");

        assertThat(spec.subagentNames()).containsExactly("security-scanner", "linting-helper");
        assertThat(spec.skillNames()).containsExactly("diff-summary");
    }

    @Test
    void generateAgentSpecGeneratesRandomNameWhenLlmNameHasNoAlphanumericCharacters() {
        var llmDraft = new AgentSpec("???", "desc", "role", "instructions", List.of(), List.of(), List.of(), null, false);
        when(promptRunner.createObject(anyString(), any(Class.class))).thenReturn(llmDraft);

        var spec = service.generateAgentSpec("concept");

        assertThat(spec.name()).startsWith("generated-");
    }

    @Test
    void generateSubagentSpecSendsConceptToLlmAndSanitizesResult() {
        var llmDraft = new SubagentSpec("Collect Application Logs", "log-agent", "Collects logs",
                "Gathers logs for the incident window", "Pull logs from the last hour", List.of("log-search"), null, null);
        when(promptRunner.createObject(anyString(), any(Class.class))).thenReturn(llmDraft);

        var spec = service.generateSubagentSpec("Collect application logs for the incident window");

        assertThat(spec.name()).isEqualTo("collect-application-logs");
        assertThat(spec.parentAgent()).isEqualTo("log-agent");
        assertThat(spec.responsibilities()).isEqualTo("Gathers logs for the incident window");
        assertThat(spec.skillNames()).containsExactly("log-search");
        assertThat(spec.mcpTools()).isEmpty();
        assertThat(capturedPrompt(SubagentSpec.class)).contains("Collect application logs for the incident window");
    }

    @Test
    void generateSubagentSpecUsesExampleConceptWhenPromptBlank() {
        var llmDraft = new SubagentSpec(null, null, null, null, null, null, null, null);
        when(promptRunner.createObject(anyString(), any(Class.class))).thenReturn(llmDraft);

        var spec = service.generateSubagentSpec("");

        assertThat(capturedPrompt(SubagentSpec.class)).contains("generic example subagent");
        assertThat(spec.parentAgent()).isEmpty();
        assertThat(spec.responsibilities()).isEmpty();
        assertThat(spec.instructions()).contains("generic example subagent");
    }

    @Test
    void generateSkillSpecSendsConceptToLlmAndSanitizesResult() {
        var llmDraft = new SkillSpec("Summarize Text", "Summarizes text", "raw text", "summary",
                "Summarize the provided text into three bullet points.", List.of(), "must not hallucinate facts");
        when(promptRunner.createObject(anyString(), any(Class.class))).thenReturn(llmDraft);

        var spec = service.generateSkillSpec("Summarize text");

        assertThat(spec.name()).isEqualTo("summarize-text");
        assertThat(spec.inputContract()).isEqualTo("raw text");
        assertThat(spec.outputContract()).isEqualTo("summary");
        assertThat(spec.policyNotes()).isEqualTo("must not hallucinate facts");
        assertThat(capturedPrompt(SkillSpec.class)).contains("Summarize text");
    }

    @Test
    void generateSkillSpecUsesExampleConceptWhenPromptBlank() {
        var llmDraft = new SkillSpec(null, null, null, null, null, null, null);
        when(promptRunner.createObject(anyString(), any(Class.class))).thenReturn(llmDraft);

        var spec = service.generateSkillSpec(null);

        assertThat(capturedPrompt(SkillSpec.class)).contains("generic example reusable skill");
        assertThat(spec.inputContract()).isEmpty();
        assertThat(spec.outputContract()).isEmpty();
        assertThat(spec.executionInstructions()).contains("generic example reusable skill");
    }

    @Test
    void truncatesLongConceptInFallbackDescriptionButKeepsFullInstructions() {
        String longPrompt = "x".repeat(250);
        var llmDraft = new AgentSpec(null, null, null, null, null, null, null, null, false);
        when(promptRunner.createObject(anyString(), any(Class.class))).thenReturn(llmDraft);

        var spec = service.generateAgentSpec(longPrompt);

        assertThat(spec.description()).hasSize(201).endsWith("…");
        assertThat(spec.instructions()).hasSize(250);
    }

    @Test
    void truncatesFirstLineUsedForNameWhenVeryLong() {
        String longFirstLine = "a".repeat(100);
        var llmDraft = new SkillSpec(null, null, null, null, null, null, null);
        when(promptRunner.createObject(anyString(), any(Class.class))).thenReturn(llmDraft);

        var spec = service.generateSkillSpec(longFirstLine);

        assertThat(spec.name()).hasSize(60);
    }
}
