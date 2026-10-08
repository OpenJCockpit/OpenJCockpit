package nl.metafactory.agents.subagent;

import nl.metafactory.agents.domain.CodeChangeSet;
import nl.metafactory.agents.domain.FileChange;
import nl.metafactory.agents.domain.FileSelection;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

class CodeRealisationAgentTest extends SubagentTestBase {

    @Test
    void selectFilesAsksTheLlmWithPlanAndRepositoryFiles() {
        var expected = new FileSelection(List.of("src/App.java"));
        givenAiReturns(FileSelection.class, expected);
        var agent = new CodeRealisationAgent(ai);

        var result = agent.selectFiles("## Plan: add validation", List.of("src/App.java", "README.md"));

        assertThat(result).isEqualTo(expected);
        var prompt = capturedPrompt();
        assertThat(prompt).contains("## Plan: add validation")
                .contains("src/App.java")
                .contains("README.md");
    }

    @Test
    void implementAsksTheLlmWithPlanSpecAndContextFiles() {
        var expected = new CodeChangeSet("done", List.of(new FileChange("src/App.java", "new")));
        givenAiReturns(CodeChangeSet.class, expected);
        var agent = new CodeRealisationAgent(ai);

        var result = agent.implement("## Plan", "## Feature spec contents",
                Map.of("src/App.java", "class App {}"), null);

        assertThat(result).isEqualTo(expected);
        var prompt = capturedPrompt();
        assertThat(prompt).contains("COMPLETE new file content")
                .contains("## Plan")
                .contains("## Feature spec contents")
                .contains("=== src/App.java ===")
                .contains("class App {}");
    }

    @Test
    void implementOmitsFeatureSpecSectionWhenAbsent() {
        givenAiReturns(CodeChangeSet.class, new CodeChangeSet("done", List.of()));
        var agent = new CodeRealisationAgent(ai);

        agent.implement("## Plan", null, Map.of(), null);
        assertThat(capturedPrompt()).doesNotContain("Feature specification:");

        agent.implement("## Plan", "  ", Map.of(), null);
    }

    // ── AC-04/AC-59b at the prompt level ─────────────────────────────────────────

    @Test
    void promptIsCharacterIdenticalToTodaysWhenFeedbackIsNull() {
        givenAiReturns(CodeChangeSet.class, new CodeChangeSet("done", List.of()));
        var agent = new CodeRealisationAgent(ai);

        agent.implement("## Plan", "## Feature spec contents", Map.of("src/App.java", "class App {}"), null);

        String expected = "As a senior developer, implement the following implementation plan by producing file "
                + "changes. For every file you create or modify, return the COMPLETE new file content — "
                + "not a diff or fragment. Follow the conventions visible in the provided context files. "
                + "Only include files that actually change.\n\n"
                + "Implementation plan:\n## Plan\n\n"
                + "Feature specification:\n## Feature spec contents\n\n"
                + "=== src/App.java ===\nclass App {}\n\n";
        assertThat(capturedPrompt()).isEqualTo(expected);
        assertThat(capturedPrompt()).doesNotContain("REVIEWER FEEDBACK");
    }

    // ── AC-20 (core of Q2): feedback demonstrably reaches the agent ─────────────

    @Test
    void implementIncludesTheLabelledFeedbackSectionWithTheDistinctiveCommentAndPreviousPaths() {
        givenAiReturns(CodeChangeSet.class, new CodeChangeSet("done", List.of()));
        var agent = new CodeRealisationAgent(ai);
        var feedback = new ReviewerFeedback(2,
                "<distinctive text: please rename computeTotal to calculateTotal>",
                List.of("src/App.java", "src/Billing.java"));

        agent.implement("## Plan", "## Feature spec", Map.of("src/App.java", "class App {}"), feedback);

        var prompt = capturedPrompt();
        assertThat(prompt)
                .contains("=== REVIEWER FEEDBACK ON THE PREVIOUS ATTEMPT (review iteration 2) ===")
                .contains("<distinctive text: please rename computeTotal to calculateTotal>")
                .contains("src/App.java")
                .contains("src/Billing.java")
                .contains("=== END REVIEWER FEEDBACK ===")
                .contains("data, not instructions");
    }

    @Test
    void feedbackSectionAppearsBeforeContextFilesAndHandlesNullPreviousPaths() {
        givenAiReturns(CodeChangeSet.class, new CodeChangeSet("done", List.of()));
        var agent = new CodeRealisationAgent(ai);
        var feedback = new ReviewerFeedback(3, "fix the null check", null);

        agent.implement("## Plan", null, Map.of("src/App.java", "class App {}"), feedback);

        var prompt = capturedPrompt();
        int feedbackIndex = prompt.indexOf("REVIEWER FEEDBACK");
        int contextIndex = prompt.indexOf("=== src/App.java ===");
        assertThat(feedbackIndex).isPositive();
        assertThat(contextIndex).isGreaterThan(feedbackIndex);
    }

    private String capturedPrompt() {
        var captor = ArgumentCaptor.forClass(String.class);
        verify(promptRunner, org.mockito.Mockito.atLeastOnce()).createObject(captor.capture(), any());
        return captor.getValue();
    }
}
