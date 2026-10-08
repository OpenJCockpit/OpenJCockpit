package nl.metafactory.aicontrol.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentCatalogUnavailableExceptionTest {

    @Test
    void eachCodeExposesItsExactFixedMessage() {
        assertThat(AgentCatalogUnavailableException.Code.UPSTREAM_UNREACHABLE.message())
                .isEqualTo("The agent catalogue could not be reached.");
        assertThat(AgentCatalogUnavailableException.Code.UPSTREAM_TIMEOUT.message())
                .isEqualTo("The agent catalogue did not respond in time.");
        assertThat(AgentCatalogUnavailableException.Code.UPSTREAM_UNAUTHORIZED.message())
                .isEqualTo("The agent catalogue rejected the request as unauthorized.");
        assertThat(AgentCatalogUnavailableException.Code.UPSTREAM_FORBIDDEN.message())
                .isEqualTo("The agent catalogue rejected the request as forbidden.");
        assertThat(AgentCatalogUnavailableException.Code.UPSTREAM_ERROR.message())
                .isEqualTo("The agent catalogue returned an unexpected error.");
        assertThat(AgentCatalogUnavailableException.Code.UPSTREAM_RESPONSE_INVALID.message())
                .isEqualTo("The agent catalogue returned a response that could not be understood.");
    }

    @Test
    void singleArgConstructorSetsCodeAndMessageFromThatCode() {
        var exception = new AgentCatalogUnavailableException(AgentCatalogUnavailableException.Code.UPSTREAM_TIMEOUT);

        assertThat(exception.code()).isEqualTo(AgentCatalogUnavailableException.Code.UPSTREAM_TIMEOUT);
        assertThat(exception.getMessage()).isEqualTo(AgentCatalogUnavailableException.Code.UPSTREAM_TIMEOUT.message());
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void causePreservingConstructorSetsCodeMessageAndTheExactCauseReference() {
        Throwable cause = new IllegalStateException("boom");

        var exception = new AgentCatalogUnavailableException(
                AgentCatalogUnavailableException.Code.UPSTREAM_UNAUTHORIZED, cause);

        assertThat(exception.code()).isEqualTo(AgentCatalogUnavailableException.Code.UPSTREAM_UNAUTHORIZED);
        assertThat(exception.getMessage())
                .isEqualTo(AgentCatalogUnavailableException.Code.UPSTREAM_UNAUTHORIZED.message());
        assertThat(exception.getCause()).isSameAs(cause);
    }
}
