package nl.metafactory.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

class JsonAccessDeniedHandlerTest {

    @Test
    void writesForbiddenWithBearerChallengeAndApiErrorBody() throws Exception {
        var response = new MockHttpServletResponse();

        new JsonAccessDeniedHandler().handle(new MockHttpServletRequest("POST", "/api/projects"), response,
                new AccessDeniedException("Access Denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader("WWW-Authenticate")).startsWith("Bearer");
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString())
                .isEqualTo(JsonAccessDeniedHandler.BODY)
                .contains("\"code\":\"FORBIDDEN\"")
                .doesNotContain("openjcockpit-");
    }
}
