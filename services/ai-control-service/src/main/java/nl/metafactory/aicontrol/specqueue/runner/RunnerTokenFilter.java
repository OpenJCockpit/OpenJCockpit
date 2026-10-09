package nl.metafactory.aicontrol.specqueue.runner;

import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

/** Always sends the runner identity; never reads the SecurityContext, so a user token can neither be relayed nor leak. */
public class RunnerTokenFilter implements ExchangeFilterFunction {

    private final RunnerTokenIssuer issuer;

    public RunnerTokenFilter(RunnerTokenIssuer issuer) {
        this.issuer = issuer;
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        return Mono.defer(() -> {
            String token = issuer.issueToken();
            return next.exchange(ClientRequest.from(request)
                    .headers(h -> h.set(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .build());
        });
    }
}
