package nl.metafactory.common.oauth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ClientCredentialsTokensTest {

    @Test
    void fetchesOnceAndReusesTheTokenUntilShortlyBeforeExpiry() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("http://kc/token")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"abc\",\"expires_in\":300}", MediaType.APPLICATION_JSON));
        var tokens = new ClientCredentialsTokens(new ClientCredentialsTokens.Config("http://kc/token", "svc", "s3cret"),
                Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC), builder.build());

        assertThat(tokens.authorization()).contains("Bearer abc");
        assertThat(tokens.authorization()).contains("Bearer abc");
        server.verify();
    }

    @Test
    void aResponseWithoutAnAccessTokenFailsInsteadOfSendingAnEmptyBearer() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://kc/token"))
                .andRespond(withSuccess("{\"token_type\":\"Bearer\"}", MediaType.APPLICATION_JSON));
        var tokens = new ClientCredentialsTokens(new ClientCredentialsTokens.Config("http://kc/token", "svc", "s3cret"),
                Clock.systemUTC(), builder.build());

        assertThatThrownBy(tokens::authorization).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no access_token");
    }

    @Test
    void unconfiguredIdentityYieldsNoTokenAndHidesTheSecret() {
        var tokens = new ClientCredentialsTokens(new ClientCredentialsTokens.Config("http://kc/token", "svc", " "));

        assertThat(tokens.configured()).isFalse();
        assertThat(tokens.authorization()).isEmpty();
        assertThat(new ClientCredentialsTokens.Config("u", "c", "s3cret").toString()).doesNotContain("s3cret");
    }
}
