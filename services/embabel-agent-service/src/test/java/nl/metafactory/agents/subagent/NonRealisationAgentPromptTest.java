package nl.metafactory.agents.subagent;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.common.PromptRunner;
import com.embabel.common.ai.model.LlmOptions;
import nl.metafactory.agents.agent.EvidenceAgent;
import nl.metafactory.agents.agent.ImpactAnalysisAgent;
import nl.metafactory.agents.agent.ImplementationAgent;
import nl.metafactory.agents.agent.RequirementAgent;
import nl.metafactory.agents.agent.ReviewAgent;
import nl.metafactory.agents.agent.TestDesignAgent;
import nl.metafactory.agents.domain.EvidenceBundle;
import nl.metafactory.agents.domain.ImpactReport;
import nl.metafactory.agents.domain.ImplementationPlan;
import nl.metafactory.agents.domain.RequirementAnalysis;
import nl.metafactory.agents.domain.ReviewReport;
import nl.metafactory.agents.domain.SpecContent;
import nl.metafactory.agents.domain.TestPlan;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AC-59b: no feedback text is ever passed to {@code RequirementAgent}, {@code ImpactAnalysisAgent},
 * {@code TestDesignAgent}, {@code ImplementationAgent}, {@code ReviewAgent}, or
 * {@code EvidenceAgent}, and their input composition is unchanged from before this feature —
 * asserted at their model boundaries. These six classes are forbidden files for this delivery
 * (work plan §4) and were never modified; {@link ImpactAnalysisAgent} is the only one that builds
 * a textual LLM prompt directly (the other five delegate to subagents with no feedback parameter
 * anywhere in their call chain, confirmed by their unchanged single-argument delegate calls).
 */
class NonRealisationAgentPromptTest {

    @Test
    void impactAnalysisAgentPromptContainsNoReviewerFeedbackMarkerOrInjectedText() {
        var ai = mock(Ai.class);
        var promptRunner = mock(PromptRunner.class);
        when(ai.withLlm(any(LlmOptions.class))).thenReturn(promptRunner);
        var expected = new ImpactReport("id", List.of("module-a"), "LOW");
        when(promptRunner.createObject(anyString(), any())).thenReturn(expected);

        var agent = new ImpactAnalysisAgent(ai);
        agent.analyzeImpact(new SpecContent("id", "file.md", "content", ""),
                new RequirementAnalysis("id", List.of("req1"), "summary"));

        var captor = ArgumentCaptor.forClass(String.class);
        verify(promptRunner).createObject(captor.capture(), any());
        assertThat(captor.getValue())
                .doesNotContain("REVIEWER FEEDBACK")
                .doesNotContain("data, not instructions")
                .isEqualTo("As an impact analysis expert, assess the system-wide impact of the following requirements. "
                        + "Spec: file.md"
                        + "\nRequirements: [req1]"
                        + "\nSummary: summary");
    }

    @Test
    void requirementAgentDelegatesWithOnlyTheSpecArgument() {
        var businessAnalystAgent = mock(BusinessAnalystAgent.class);
        var spec = new SpecContent("id", "file.md", "content", "");
        var expected = new RequirementAnalysis("id", List.of("req1"), "summary");
        when(businessAnalystAgent.analyze(spec)).thenReturn(expected);

        new RequirementAgent(businessAnalystAgent).analyzeRequirements(spec);

        verify(businessAnalystAgent).analyze(spec);
    }

    @Test
    void testDesignAgentDelegatesWithOnlyTheRequirementsArgument() {
        var testEngineerAgent = mock(TestEngineerAgent.class);
        var qaEngineerAgent = mock(QaEngineerAgent.class);
        var requirements = new RequirementAnalysis("id", List.of("req1"), "summary");
        when(testEngineerAgent.design(requirements)).thenReturn(new TestPlan("id", List.of("eng"), "80%"));
        when(qaEngineerAgent.review(requirements)).thenReturn(new TestPlan("id", List.of("qa"), "90%"));

        new TestDesignAgent(testEngineerAgent, qaEngineerAgent).designTests(requirements);

        verify(testEngineerAgent).design(requirements);
        verify(qaEngineerAgent).review(requirements);
    }

    @Test
    void implementationAgentDelegatesWithoutAnyFeedbackParameter() {
        var architect = mock(SoftwareArchitectAgent.class);
        var developer = mock(DeveloperAgent.class);
        var leadDev = mock(LeadDeveloperAgent.class);
        var impact = new ImpactReport("id", List.of("m1"), "LOW");
        var spec = new SpecContent("id", "file.md", "content", "");
        when(architect.defineArchitecture(impact)).thenReturn(new ImplementationPlan("id", List.of("a"), "arch"));
        when(developer.propose(spec)).thenReturn(new ImplementationPlan("id", List.of("d"), ""));
        when(leadDev.refine(any())).thenReturn(new ImplementationPlan("id", List.of("a", "d"), "arch"));

        new ImplementationAgent(architect, developer, leadDev).plan(spec, impact);

        verify(architect).defineArchitecture(impact);
        verify(developer).propose(spec);
    }

    @Test
    void reviewAgentDelegatesWithOnlyThePlanArgument() {
        var architect = mock(SoftwareArchitectAgent.class);
        var developer = mock(DeveloperAgent.class);
        var leadDev = mock(LeadDeveloperAgent.class);
        var plan = new ImplementationPlan("id", List.of("change"), "arch");
        var tests = new TestPlan("id", List.of("t"), "90%");
        when(architect.reviewPlan(plan)).thenReturn(new ReviewReport("id", true, List.of()));
        when(developer.reviewPlan(plan)).thenReturn(new ReviewReport("id", true, List.of()));
        when(leadDev.reviewPlan(plan)).thenReturn(new ReviewReport("id", true, List.of()));

        new ReviewAgent(architect, developer, leadDev).review(plan, tests);

        verify(architect).reviewPlan(plan);
        verify(developer).reviewPlan(plan);
        verify(leadDev).reviewPlan(plan);
    }

    @Test
    void evidenceAgentDelegatesWithoutAnyFeedbackParameter() {
        var logCollector = mock(LogCollectorAgent.class);
        var actionCollector = mock(ActionCollectorAgent.class);
        var eventCollector = mock(EventCollectorAgent.class);
        var finalBundle = new EvidenceBundle("id", List.of(), Instant.now());
        when(logCollector.collectLogs(any())).thenAnswer(inv -> inv.getArgument(0));
        when(actionCollector.collectActions(any())).thenAnswer(inv -> inv.getArgument(0));
        when(eventCollector.collectEvents(any())).thenReturn(finalBundle);

        new EvidenceAgent(logCollector, actionCollector, eventCollector).compileEvidence(
                new RequirementAnalysis("id", List.of("r"), "sum"),
                new ImpactReport("id", List.of("m"), "LOW"),
                new TestPlan("id", List.of("t"), "90%"),
                new ImplementationPlan("id", List.of("c"), "arch"),
                new ReviewReport("id", true, List.of()));

        verify(logCollector).collectLogs(any());
        verify(actionCollector).collectActions(any());
        verify(eventCollector).collectEvents(any());
    }
}
