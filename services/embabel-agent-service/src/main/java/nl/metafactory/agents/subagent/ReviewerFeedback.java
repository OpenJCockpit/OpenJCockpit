package nl.metafactory.agents.subagent;

import java.util.List;

/**
 * The operator's feedback on the previous realisation attempt, threaded into
 * {@link CodeRealisationAgent#implement} as a labelled, delimited "data, not instructions" prompt
 * section (architecture §7.5, AC-20, AC-41). Never {@code null} for iteration ≥2; always
 * {@code null} for iteration 1, so the prompt stays character-identical to before this feature
 * when there is no feedback (AC-04/AC-59b at the prompt level).
 */
public record ReviewerFeedback(int iteration, String comment, List<String> previousChangedPaths) {
}
