package nl.metafactory.agents.a2a.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The A2A providers this service connects to; one {@link A2AProviderClient} is created per entry.
 * Keys are never written here: {@code api-key} is bound from an environment variable.
 */
@Component
@ConfigurationProperties("openjcockpit")
public class A2AProvidersProperties {

    private List<Provider> a2aProviders = new ArrayList<>();

    public List<Provider> getA2aProviders() {
        return a2aProviders;
    }

    public void setA2aProviders(List<Provider> a2aProviders) {
        this.a2aProviders = a2aProviders;
    }

    public static class Provider {

        /** Provider implementation; currently {@code litellm}. */
        private String type;

        /** Provider id used in agent references ({@code a2a:<id>:<agent>}); defaults to the type. */
        private String id;

        /** Header shown in the dashboard. */
        private String name;

        /** Address this service uses, e.g. {@code http://litellm:4000}. */
        private String baseUrl;

        /** Address users use, e.g. {@code http://localhost:4100}; defaults to base-url. */
        private String publicUrl;

        /** The provider's single A2A key for this platform. */
        private String apiKey;

        private Duration connectTimeout = Duration.ofSeconds(3);

        private Duration readTimeout = Duration.ofSeconds(10);

        /** Read timeout for {@code message/send}: an agent run includes an LLM call. */
        private Duration messageTimeout = Duration.ofMinutes(10);

        /**
         * OAuth2 client-credentials identity this service uses when it calls agents through this
         * provider (workflow runs). The token is forwarded to the agent by the provider.
         */
        private CallerAuth callerAuth = new CallerAuth();

        public CallerAuth getCallerAuth() {
            return callerAuth;
        }

        public void setCallerAuth(CallerAuth callerAuth) {
            this.callerAuth = callerAuth;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        /** The id, or the type when no id is set (the provider from configuration keeps id = type). */
        public String effectiveId() {
            return id != null && !id.isBlank() ? id : type;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getPublicUrl() {
            return publicUrl;
        }

        public void setPublicUrl(String publicUrl) {
            this.publicUrl = publicUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }

        public Duration getMessageTimeout() {
            return messageTimeout;
        }

        public void setMessageTimeout(Duration messageTimeout) {
            this.messageTimeout = messageTimeout;
        }

        /** Public URL without trailing slash, falling back to the base URL. */
        public String effectivePublicUrl() {
            String url = publicUrl != null && !publicUrl.isBlank() ? publicUrl : baseUrl;
            return url == null ? null : url.replaceAll("/+$", "");
        }

        public static class CallerAuth {

            private String tokenUrl;
            private String clientId;
            private String clientSecret;

            public boolean configured() {
                return tokenUrl != null && !tokenUrl.isBlank() && clientId != null && !clientId.isBlank()
                        && clientSecret != null && !clientSecret.isBlank();
            }

            public String getTokenUrl() {
                return tokenUrl;
            }

            public void setTokenUrl(String tokenUrl) {
                this.tokenUrl = tokenUrl;
            }

            public String getClientId() {
                return clientId;
            }

            public void setClientId(String clientId) {
                this.clientId = clientId;
            }

            public String getClientSecret() {
                return clientSecret;
            }

            public void setClientSecret(String clientSecret) {
                this.clientSecret = clientSecret;
            }

            @Override
            public String toString() {
                return "CallerAuth{tokenUrl=" + tokenUrl + ", clientId=" + clientId + ", clientSecret="
                        + (clientSecret == null ? "null" : "***") + "}";
            }
        }

        @Override
        public String toString() {
            return "Provider{type=" + type + ", id=" + id + ", name=" + name + ", baseUrl=" + baseUrl + ", apiKey="
                    + (apiKey == null ? "null" : "***") + "}";
        }
    }
}
