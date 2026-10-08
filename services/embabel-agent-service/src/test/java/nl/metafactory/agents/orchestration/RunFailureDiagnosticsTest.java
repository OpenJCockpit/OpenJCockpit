package nl.metafactory.agents.orchestration;

import jakarta.persistence.Column;
import nl.metafactory.agents.persistence.AgentRunRecord;
import nl.metafactory.agents.workflow.DefinitionFileReadException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RunFailureDiagnosticsTest {

    private static final String ABSOLUTE_PATH = "/Users/victim/secret/repo/.git/config";
    private static final String CREDENTIALED_URL = "https://someuser:sometoken123@git.example.com/org/repo.git";
    private static final String TOKEN_LIKE = "ghp_aaaaBBBBccccDDDDeeeeFFFFgggg1111";
    private static final String UNIQUE_MARKER = "UNIQUE_MARKER_7f3a9c";

    private static DefinitionFileReadException newDefinitionFileReadException(String relativeName, String sanitisedReason) throws Exception {
        Constructor<DefinitionFileReadException> constructor = DefinitionFileReadException.class.getDeclaredConstructor(
                String.class, String.class, IOException.class);
        constructor.setAccessible(true);
        return constructor.newInstance(relativeName, sanitisedReason, new IOException("irrelevant"));
    }

    @Test
    void summariseReturnsNullForNullInput() {
        assertThat(RunFailureDiagnostics.summarise(null)).isNull();
    }

    @Test
    void summariseOfSingleThrowableWithNoCauseYieldsItsSimpleClassNameOnly() {
        assertThat(RunFailureDiagnostics.summarise(new IllegalStateException("irrelevant")))
                .isEqualTo("IllegalStateException");
    }

    @Test
    void summariseOfChainOfExactlyFiveYieldsAllFiveNamesJoinedWithNoTruncationMarker() {
        Throwable cause4 = new RuntimeException("m4");
        Throwable cause3 = new RuntimeException("m3", cause4);
        Throwable cause2 = new RuntimeException("m2", cause3);
        Throwable cause1 = new RuntimeException("m1", cause2);
        Throwable outer = new RuntimeException("m0", cause1);

        String result = RunFailureDiagnostics.summarise(outer);

        assertThat(result).isEqualTo("RuntimeException <- RuntimeException <- RuntimeException <- RuntimeException <- RuntimeException");
        assertThat(result).doesNotEndWith("...");
    }

    @Test
    void summariseOfChainOfSixOrMoreTruncatesAtFiveWithMarker() {
        Throwable cause5 = new RuntimeException("m5");
        Throwable cause4 = new RuntimeException("m4", cause5);
        Throwable cause3 = new RuntimeException("m3", cause4);
        Throwable cause2 = new RuntimeException("m2", cause3);
        Throwable cause1 = new RuntimeException("m1", cause2);
        Throwable outer = new RuntimeException("m0", cause1);

        String result = RunFailureDiagnostics.summarise(outer);

        assertThat(result).isEqualTo("RuntimeException <- RuntimeException <- RuntimeException <- RuntimeException <- RuntimeException <- ...");
    }

    @Test
    void summariseTerminatesOnACyclicCauseChain() throws Exception {
        CyclicThrowable a = new CyclicThrowable("a");
        CyclicThrowable b = new CyclicThrowable("b");
        a.wiredCause = b;
        b.wiredCause = a;

        String result = assertTimeoutBounded(() -> RunFailureDiagnostics.summarise(a));

        assertThat(result).isNotNull();
        assertThat(result.length()).isLessThanOrEqualTo(RunFailureDiagnostics.MAX_LENGTH);
    }

    private static String assertTimeoutBounded(java.util.function.Supplier<String> supplier) {
        return org.junit.jupiter.api.Assertions.assertTimeout(java.time.Duration.ofSeconds(5), supplier::get);
    }

    private static final class CyclicThrowable extends RuntimeException {
        private Throwable wiredCause;

        CyclicThrowable(String message) {
            super(message);
        }

        @Override
        public synchronized Throwable getCause() {
            return wiredCause;
        }
    }

    @Test
    void summariseOfBlankSimpleClassNameYieldsUnknownThrowable() {
        RuntimeException anonymous = new RuntimeException("x") {
        };

        assertThat(RunFailureDiagnostics.summarise(anonymous)).isEqualTo("UnknownThrowable");
    }

    @Test
    void summariseOfDefinitionFileReadExceptionAppendsRelativeNameAndSanitisedReason() throws Exception {
        DefinitionFileReadException exception = newDefinitionFileReadException(
                "workflows/wf-x.yaml", "StreamReadException at line 7, column 3");

        String result = RunFailureDiagnostics.summarise(exception);

        assertThat(result).endsWith(" (workflows/wf-x.yaml: StreamReadException at line 7, column 3)");
        assertThat(result).startsWith("DefinitionFileReadException");
    }

    @Test
    void summariseOfResponseStatusExceptionAppendsHttpStatus() {
        ResponseStatusException exception = new ResponseStatusException(HttpStatus.BAD_GATEWAY);

        assertThat(RunFailureDiagnostics.summarise(exception)).endsWith(" (HTTP 502)");
    }

    @Test
    void summariseOfRestClientResponseExceptionAppendsHttpStatus() {
        RestClientResponseException exception = new RestClientResponseException(
                "service unavailable", 503, "Service Unavailable", null, null, null);

        assertThat(RunFailureDiagnostics.summarise(exception)).endsWith(" (HTTP 503)");
    }

    @Test
    void summariseOfNonAllowListedExceptionAppendsNoReasonText() {
        String result = RunFailureDiagnostics.summarise(new RuntimeException("something"));

        assertThat(result).isEqualTo("RuntimeException");
        assertThat(result).doesNotContain("(");
        assertThat(result).doesNotContain("HTTP");
    }

    @Test
    void summariseNeverExceedsMaxLength() {
        String result = RunFailureDiagnostics.summarise(new IllegalStateException("short"));

        assertThat(result.length()).isLessThanOrEqualTo(RunFailureDiagnostics.MAX_LENGTH);
    }

    @Test
    void summariseOfVeryLongChainTruncatesToExactlyMaxLengthWithMarker() throws Exception {
        DefinitionFileReadException exception = newDefinitionFileReadException(
                "workflows/a-very-long-relative-file-name-used-purely-to-force-truncation-of-the-bounded-summary-output-so-that-the-total-length-comfortably-exceeds-the-five-hundred-and-twelve-character-budget-enforced-by-run-failure-diagnostics-dot-java.yaml",
                "StreamReadException with an unusually long sanitised reason string repeated to push the total combined length of the class chain plus this parenthetical reason well past the five hundred and twelve character maximum length budget that RunFailureDiagnostics enforces on every summary it produces regardless of how long the inputs are");

        String result = RunFailureDiagnostics.summarise(exception);

        assertThat(result.length()).isEqualTo(RunFailureDiagnostics.MAX_LENGTH);
        assertThat(result).endsWith("...");
    }

    @Test
    void summariseOutputMatchesClosedAlphabet() throws Exception {
        DefinitionFileReadException exception = newDefinitionFileReadException(
                "workflows/wf-x.yaml", "StreamReadException at line 7, column 3");

        String result = RunFailureDiagnostics.summarise(exception);

        assertThat(result).matches("^[A-Za-z0-9$_ .,()<:\\-/]*$");
    }

    @Test
    void failureSummaryColumnLengthMatchesMaxLengthConstant() throws NoSuchFieldException {
        Field field = AgentRunRecord.class.getDeclaredField("failureSummary");
        Column column = field.getAnnotation(Column.class);

        assertThat(column.length()).isEqualTo(RunFailureDiagnostics.MAX_LENGTH);
    }

    @Test
    void summariseNeverLeaksRawMessageOrPathOrCredentialFromAnyDepthOfCauseChain() {
        Throwable deepCause = new IllegalArgumentException(
                "path=" + ABSOLUTE_PATH + " url=" + CREDENTIALED_URL + " token=" + TOKEN_LIKE + " marker=" + UNIQUE_MARKER);
        Throwable middle = new RuntimeException("wrapping", deepCause);
        Throwable outer = new IllegalStateException("outer failure", middle);

        String result = RunFailureDiagnostics.summarise(outer);

        assertThat(result).doesNotContain(ABSOLUTE_PATH);
        assertThat(result).doesNotContain(CREDENTIALED_URL);
        assertThat(result).doesNotContain(TOKEN_LIKE);
        assertThat(result).doesNotContain(UNIQUE_MARKER);
    }
}
