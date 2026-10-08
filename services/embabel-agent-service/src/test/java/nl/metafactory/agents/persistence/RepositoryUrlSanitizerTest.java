package nl.metafactory.agents.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class RepositoryUrlSanitizerTest {

    @Test
    void masksEmbeddedCredentials() {
        String result = RepositoryUrlSanitizer.mask("https://user:token@github.com/org/repo.git");
        assertEquals("https://***@github.com/org/repo.git", result);
    }

    @Test
    void passesThroughUrlWithNoCredentials() {
        String url = "https://github.com/org/repo.git";
        assertEquals(url, RepositoryUrlSanitizer.mask(url));
    }

    @Test
    void returnsRealNullForNullInput() {
        String result = RepositoryUrlSanitizer.mask(null);
        assertNull(result);
        assertNotEquals("<null>", result);
    }
}
