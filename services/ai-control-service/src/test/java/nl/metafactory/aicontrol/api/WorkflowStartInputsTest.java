package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.client.WorkflowStartInputDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class WorkflowStartInputsTest {

    private static WorkflowStartInputDto withBaseBranch(String baseBranch) {
        return new WorkflowStartInputDto("prompt", "spec.md", "https://github.com/org/repo.git",
                "11111111-2222-3333-4444-555555555555", "bot", "s3cr3t-token", baseBranch);
    }

    @Test
    void absentBaseBranchPassesThroughUntouched() {
        var input = withBaseBranch(null);
        assertThat(WorkflowStartInputs.validateCallerBaseBranch(input)).isSameAs(input);
    }

    @Test
    void alreadyTrimmedAcceptableValuePassesThroughSameInstance() {
        var input = withBaseBranch("develop");
        assertThat(WorkflowStartInputs.validateCallerBaseBranch(input)).isSameAs(input);
    }

    @ParameterizedTest
    @ValueSource(strings = {"develop", "feature/MADP-54", "release/2026.1", "a-b", "main"})
    void acceptableBranchNamesAreAccepted(String value) {
        var result = WorkflowStartInputs.validateCallerBaseBranch(withBaseBranch(value));
        assertThat(result.baseBranch()).isEqualTo(value);
    }

    @Test
    void surroundingWhitespaceIsTrimmedRatherThanRejected() {
        var result = WorkflowStartInputs.validateCallerBaseBranch(withBaseBranch("  develop  "));
        assertThat(result.baseBranch()).isEqualTo("develop");
        assertThat(result.gitToken()).isEqualTo("s3cr3t-token");
        assertThat(result.projectId()).isEqualTo("11111111-2222-3333-4444-555555555555");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "my branch", "a b", "a\tb", "-lead", "a..b", "..", "-", "a\u001fb", "a\u007fb"})
    void malformedCallerBaseBranchIsRejectedWith400(String value) {
        assertThatExceptionOfType(ResponseStatusException.class)
                .isThrownBy(() -> WorkflowStartInputs.validateCallerBaseBranch(withBaseBranch(value)))
                .satisfies(ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void overLengthBranchNameIsRejected() {
        String tooLong = "a".repeat(WorkflowStartInputs.MAX + 1);
        assertThatExceptionOfType(ResponseStatusException.class)
                .isThrownBy(() -> WorkflowStartInputs.validateCallerBaseBranch(withBaseBranch(tooLong)));
    }

    @Test
    void maxLengthBranchNameIsAccepted() {
        String atLimit = "a".repeat(WorkflowStartInputs.MAX);
        var result = WorkflowStartInputs.validateCallerBaseBranch(withBaseBranch(atLimit));
        assertThat(result.baseBranch()).isEqualTo(atLimit);
    }

    @Test
    void the400MessageEchoesNeitherTheSubmittedValueNorAnyCredential() {
        assertThatExceptionOfType(ResponseStatusException.class)
                .isThrownBy(() -> WorkflowStartInputs.validateCallerBaseBranch(withBaseBranch("secret-branch name")))
                .satisfies(ex -> assertThat(ex.getReason())
                        .doesNotContain("secret-branch name")
                        .doesNotContain("s3cr3t-token")
                        .doesNotContain("bot"));
    }

    @Test
    void isAcceptableBranchNameCoversEachClause() {
        assertThat(WorkflowStartInputs.isAcceptableBranchName("develop")).isTrue();
        assertThat(WorkflowStartInputs.isAcceptableBranchName("")).isFalse();
        assertThat(WorkflowStartInputs.isAcceptableBranchName("a".repeat(WorkflowStartInputs.MAX + 1))).isFalse();
        assertThat(WorkflowStartInputs.isAcceptableBranchName("a b")).isFalse();
        assertThat(WorkflowStartInputs.isAcceptableBranchName("-x")).isFalse();
        assertThat(WorkflowStartInputs.isAcceptableBranchName("a..b")).isFalse();
        assertThat(WorkflowStartInputs.isAcceptableBranchName("a\u001fb")).isFalse();
        assertThat(WorkflowStartInputs.isAcceptableBranchName("a\u007fb")).isFalse();
    }
}
