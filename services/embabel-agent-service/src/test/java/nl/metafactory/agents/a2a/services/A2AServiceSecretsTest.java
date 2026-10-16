package nl.metafactory.agents.a2a.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class A2AServiceSecretsTest {

    @Test
    void encryptsAndDecryptsWithTheConfiguredKey() {
        var secrets = new A2AServiceSecrets("test-a2a-services-secret", "5c0744940b5c369b");
        String cipher = secrets.encrypt("sk-value");
        assertThat(cipher).doesNotContain("sk-value");
        assertThat(secrets.decrypt(cipher)).contains("sk-value");
        assertThat(secrets.decrypt("not-a-ciphertext")).isEmpty();
        assertThat(new A2AServiceSecrets("another-secret-of-enough-length", "5c0744940b5c369b").decrypt(cipher)).isEmpty();
    }

    @Test
    void aMissingShortOrMalformedKeyMeansNoSecretsAtAll() {
        assertThat(new A2AServiceSecrets("", "5c0744940b5c369b").available()).isFalse();
        assertThat(new A2AServiceSecrets("too-short", "5c0744940b5c369b").available()).isFalse();
        assertThat(new A2AServiceSecrets("test-a2a-services-secret", "").available()).isFalse();
        assertThat(new A2AServiceSecrets("test-a2a-services-secret", "not-hex").available()).isFalse();
        assertThat(new A2AServiceSecrets("", "").decrypt("x")).isEmpty();
    }
}
