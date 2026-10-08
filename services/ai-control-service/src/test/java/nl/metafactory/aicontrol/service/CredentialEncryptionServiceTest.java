package nl.metafactory.aicontrol.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialEncryptionServiceTest {

    private CredentialEncryptionService service;

    @BeforeEach
    void setUp() {
        service = new CredentialEncryptionService("test-only-secret-32-characters!!", "0123456789abcdef");
    }

    @Test
    void encryptDecryptRoundtripReturnsOriginalValue() {
        String plaintext = "my-github-pat-token";
        String encrypted = service.encrypt(plaintext);
        String decrypted = service.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void differentPlaintextsProduceDifferentCiphertexts() {
        String cipher1 = service.encrypt("token-a");
        String cipher2 = service.encrypt("token-b");
        assertThat(cipher1).isNotEqualTo(cipher2);
    }

    @Test
    void encryptedValueDoesNotContainPlaintext() {
        String plaintext = "super-secret-token";
        String encrypted = service.encrypt(plaintext);
        assertThat(encrypted).doesNotContain(plaintext);
    }
}