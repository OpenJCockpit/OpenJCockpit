package nl.metafactory.common.security;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Refuses to start a service under the {@code prod} profile when its configuration still carries
 * development assumptions: the {@code local} profile active at the same time, a required secret that is
 * blank, short or one of the repository's well-known local placeholders, or an endpoint that must be HTTPS
 * but is not. Each service lists its own keys in {@code application-prod.yml}
 * ({@code openjcockpit.production-guard.*}). Violations name the property, never its value.
 */
@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class ProductionConfigGuard implements InitializingBean {

    static final int MIN_SECRET_LENGTH = 16;

    /** Fragments of the seeded local values in docker-compose.yml, .env.example and the realm import. */
    static final Pattern DEV_PLACEHOLDER = Pattern.compile(
            "(?i).*(local|dev-only|change-?me|change-this|welkom|test-only|example|placeholder).*"
                    + "|admin|keycloak|0123456789abcdef|a2a5e7c1ce5a17ed");

    private final Environment environment;

    public ProductionConfigGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> violations = violations(environment);
        if (!violations.isEmpty()) {
            throw new IllegalStateException("Production configuration rejected (profile 'prod'): "
                    + String.join("; ", violations));
        }
    }

    static List<String> violations(Environment env) {
        List<String> violations = new ArrayList<>();
        if (env.acceptsProfiles(Profiles.of("local"))) {
            violations.add("profile 'local' must not be active together with 'prod'");
        }
        for (String key : env.getProperty("openjcockpit.production-guard.required-secrets", String[].class, new String[0])) {
            String value = env.getProperty(key.trim());
            if (value == null || value.isBlank()) {
                violations.add(key + " is not set");
            } else if (value.length() < MIN_SECRET_LENGTH) {
                violations.add(key + " is shorter than " + MIN_SECRET_LENGTH + " characters");
            } else if (DEV_PLACEHOLDER.matcher(value).matches()) {
                violations.add(key + " is a development placeholder");
            }
        }
        for (String key : env.getProperty("openjcockpit.production-guard.https-urls", String[].class, new String[0])) {
            String value = env.getProperty(key.trim());
            if (value == null || !value.startsWith("https://")) {
                violations.add(key + " must be an https:// URL");
            }
        }
        return violations;
    }
}
