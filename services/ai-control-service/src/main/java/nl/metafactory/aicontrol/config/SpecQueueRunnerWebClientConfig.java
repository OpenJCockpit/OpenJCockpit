package nl.metafactory.aicontrol.config;

import nl.metafactory.aicontrol.specqueue.runner.RunnerTokenFilter;
import nl.metafactory.aicontrol.specqueue.runner.RunnerTokenIssuer;
import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Separate from the user-relay embabelWebClient: this one always authenticates as the runner service identity. */
@Configuration
public class SpecQueueRunnerWebClientConfig {

    @Bean
    public WebClient embabelRunnerWebClient(
            @Value("${openjcockpit.embabel-agent-service.base-url}") String baseUrl,
            SpecQueueProperties properties,
            RunnerTokenIssuer tokenIssuer) {
        var timeout = properties.getRunner().getEmbabelTimeout();
        var mapper = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        var strategies = ExchangeStrategies.builder()
                .codecs(c -> {
                    c.defaultCodecs().jacksonJsonDecoder(new JacksonJsonDecoder(mapper));
                    c.defaultCodecs().jacksonJsonEncoder(new JacksonJsonEncoder(mapper));
                })
                .build();
        var httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) timeout.toMillis())
                .responseTimeout(timeout);
        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .filter(new RunnerTokenFilter(tokenIssuer))
                .build();
    }
}
