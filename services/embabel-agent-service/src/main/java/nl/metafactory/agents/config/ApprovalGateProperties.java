package nl.metafactory.agents.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Frozen Contract #2 (workflow-approval-gate work plan §5): every default here is also a Java
 * field initialiser, so a truncated or absent {@code openjcockpit.approval-gate} YAML block cannot
 * produce a permissive value. Follows the same {@code @Component @ConfigurationProperties} pattern
 * as {@link AgentPipelineProperties}/{@link WorkflowDefinitionProperties} — no explicit
 * {@code @EnableConfigurationProperties} registration is needed or added.
 */
@Component
@ConfigurationProperties("openjcockpit.approval-gate")
public class ApprovalGateProperties {

    /** BR-27/A13: at most this many feedback iterations per gate (i.e. at most +1 gate openings). */
    private int maxFeedbackIterations = 3;

    /** BR-30/A14: maximum accepted comment length. */
    private int commentMaxLength = 4000;

    /** AC-13: changed-path list truncation bound for the approval context payload. */
    private int maxChangedPathsInContext = 50;

    public int getMaxFeedbackIterations() {
        return maxFeedbackIterations;
    }

    public void setMaxFeedbackIterations(int maxFeedbackIterations) {
        this.maxFeedbackIterations = maxFeedbackIterations;
    }

    public int getCommentMaxLength() {
        return commentMaxLength;
    }

    public void setCommentMaxLength(int commentMaxLength) {
        this.commentMaxLength = commentMaxLength;
    }

    public int getMaxChangedPathsInContext() {
        return maxChangedPathsInContext;
    }

    public void setMaxChangedPathsInContext(int maxChangedPathsInContext) {
        this.maxChangedPathsInContext = maxChangedPathsInContext;
    }
}
