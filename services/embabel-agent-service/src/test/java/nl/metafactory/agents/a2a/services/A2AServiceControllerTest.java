package nl.metafactory.agents.a2a.services;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class A2AServiceControllerTest {

    final A2AServices services = mock(A2AServices.class);
    final A2AServiceController controller = new A2AServiceController(services);
    final A2AServiceRequest request = new A2AServiceRequest("acme", "Acme", "https://gw.example", null, "k", null, null, null);

    static Jwt jwt(String subject) {
        var builder = Jwt.withTokenValue("t").header("alg", "none").issuedAt(Instant.now()).claim("azp", "openjcockpit");
        if (subject != null) {
            builder.subject(subject);
        }
        return builder.build();
    }

    @Test
    void changesAreAttributedToTheCallersSubject() {
        controller.create(request, jwt("user-1"));
        verify(services).create(request, "user-1");
        controller.update("acme", request, jwt("user-2"));
        verify(services).update("acme", request, "user-2");
        assertThat(controller.delete("acme", jwt("user-3")).getStatusCode().value()).isEqualTo(204);
        verify(services).delete("acme");
    }

    @Test
    void withoutASubjectNothingChanges() {
        assertThatThrownBy(() -> controller.create(request, null)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
        assertThatThrownBy(() -> controller.update("acme", request, jwt(null))).hasMessageContaining("401");
        assertThatThrownBy(() -> controller.delete("acme", jwt(null))).hasMessageContaining("401");
        verifyNoInteractions(services);
    }

    @Test
    void requestsNeverRenderSecrets() {
        var r = new A2AServiceRequest("acme", "Acme", "https://gw.example", null, "sk-secret", "https://idp/token",
                "client", "client-secret");
        assertThat(r.toString()).doesNotContain("sk-secret").doesNotContain("client-secret\"").contains("***");
        assertThat(r.toString()).doesNotContain("=client-secret");
    }
}
