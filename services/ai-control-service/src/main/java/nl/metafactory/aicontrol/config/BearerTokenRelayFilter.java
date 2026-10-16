package nl.metafactory.aicontrol.config;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

/**
 * Relays the current user's token to the agent service. A request that already carries its own
 * {@code Authorization} (a call made explicitly as ai-control's service identity) is left as it is, so
 * a service call made while serving a user request never carries the user's token as well.
 */
public class BearerTokenRelayFilter implements ExchangeFilterFunction {

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        if (request.headers().containsHeader(HttpHeaders.AUTHORIZATION)) {
            return next.exchange(request);
        }
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return next.exchange(
                ClientRequest.from(request)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAuth.getToken().getTokenValue())
                    .build()
            );
        }
        return next.exchange(request);
    }
}
