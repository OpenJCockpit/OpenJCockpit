package nl.metafactory.agents.a2a.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/**
 * Encrypts the secrets of A2A services added in the dashboard (AES-GCM, key derived from
 * {@code openjcockpit.a2a-services.encryption-secret} and {@code -salt}). Without both, no secret can be
 * stored or read (fail closed). Neither secrets, ciphertext nor exception messages are ever logged.
 */
@Component
public class A2AServiceSecrets {

    private static final Logger log = LoggerFactory.getLogger(A2AServiceSecrets.class);
    static final int MIN_SECRET_LENGTH = 16;

    private final TextEncryptor encryptor;

    public A2AServiceSecrets(@Value("${openjcockpit.a2a-services.encryption-secret:}") String secret,
                             @Value("${openjcockpit.a2a-services.encryption-salt:}") String salt) {
        this.encryptor = create(secret, salt);
    }

    private static TextEncryptor create(String secret, String salt) {
        if (secret == null || secret.length() < MIN_SECRET_LENGTH || salt == null || salt.isBlank()) {
            log.warn("a2a.services.secrets unavailable: encryption secret (min {} chars) or salt not set", MIN_SECRET_LENGTH);
            return null;
        }
        try {
            return Encryptors.delux(secret, salt);
        } catch (RuntimeException e) {
            log.warn("a2a.services.secrets unavailable: encryption salt must be hex-encoded");
            return null;
        }
    }

    public boolean available() {
        return encryptor != null;
    }

    /** 503 when secrets cannot be stored in this environment. */
    public String encrypt(String plaintext) {
        if (encryptor == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "A2A service secrets cannot be stored: A2A_SERVICE_ENCRYPTION_SECRET and A2A_SERVICE_ENCRYPTION_SALT are not set");
        }
        return encryptor.encrypt(plaintext);
    }

    /** Empty when secrets are unavailable or the ciphertext cannot be decrypted (e.g. the key changed). */
    public Optional<String> decrypt(String ciphertext) {
        if (encryptor == null || ciphertext == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(encryptor.decrypt(ciphertext));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
