package nl.metafactory.aicontrol.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GitConnectionCheckRequestTest {

    @Test
    void toStringNeverContainsTheSecretOrTheUrl() {
        var req = new GitConnectionCheckRequest("https://user:pw@git.example.com/org/repo",
                GitCredentialType.HTTPS_TOKEN, "bob", "s3cret", null);
        assertThat(req.toString()).doesNotContain("s3cret", "pw", "git.example.com", "bob");
    }
}
