package nl.metafactory.aicontrol.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class WebClientConfig {

    @Bean
    WebClient embabelWebClient(
            @Value("${metafactory.embabel-agent-service.base-url}") String baseUrl) {
        var mapper = JsonMapper.builder().build();
        var strategies = ExchangeStrategies.builder()
                .codecs(c -> {
                    c.defaultCodecs().jacksonJsonDecoder(new JacksonJsonDecoder(mapper));
                    c.defaultCodecs().jacksonJsonEncoder(new JacksonJsonEncoder(mapper));
                })
                .build();
        return WebClient.builder()
                .baseUrl(baseUrl)
                .exchangeStrategies(strategies)
                .filter(new BearerTokenRelayFilter())
                .build();
    }
}