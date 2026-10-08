package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.client.WorkflowStartInputDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Q3 (MADP-54): a light, stateless sanity check on a <em>caller-supplied</em> {@code baseBranch}
 * at the {@code POST /api/workflows/{id}/start} boundary. It is deliberately not a full
 * {@code git check-ref-format} and it never rewrites silently: a violation is a {@code 400}
 * with a message that echoes neither the submitted value nor any credential, and no downstream
 * call is made. A value that later turns out not to exist on the remote passes here and fails
 * later with the publisher's Q4 message.
 *
 * <p>Invoked from {@link WorkflowController#start} rather than from the enrichment service,
 * because the controller is the only place where "caller-supplied" is definitionally true —
 * after enrichment the value may be admin-curated.
 */
final class WorkflowStartInputs {

    /** Defensive upper bound; aligns with the {@code project.default_branch} column. */
    static final int MAX = 255;

    private static final String MESSAGE = "Invalid baseBranch: must be a non-blank branch name without "
            + "whitespace, not starting with '-', without '..' and without control characters";

    private WorkflowStartInputs() {
    }

    /** Returns the input with a trimmed {@code baseBranch}, or throws {@code 400}. */
    static WorkflowStartInputDto validateCallerBaseBranch(WorkflowStartInputDto input) {
        String raw = input.baseBranch();
        if (raw == null) {
            return input;
        }
        String trimmed = raw.trim();
        if (!isAcceptableBranchName(trimmed)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MESSAGE);
        }
        if (trimmed.equals(raw)) {
            return input;
        }
        return new WorkflowStartInputDto(input.prompt(), input.specFile(), input.repositoryUrl(),
                input.projectId(), input.gitUsername(), input.gitToken(), trimmed);
    }

    /** {@code t} is already trimmed. */
    static boolean isAcceptableBranchName(String t) {
        return !t.isEmpty()
                && t.length() <= MAX
                && t.codePoints().noneMatch(Character::isWhitespace)
                && !t.startsWith("-")
                && !t.contains("..")
                && t.codePoints().noneMatch(c -> c < 0x20 || c == 0x7F);
    }
}
