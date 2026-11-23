package nl.metafactory.aicontrol.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitHostClassifierTest {

    @ParameterizedTest
    @ValueSource(strings = {"https://127.0.0.1/r", "ssh://[::1]/r", "git://0.0.0.0/r", "https://localhost/r"})
    void flagsLoopback(String url) throws Exception {
        assertThat(GitHostClassifier.classifyGitUrl(url).warnings()).containsExactly("host_loopback");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://169.254.169.254/latest", "https://[fe80::1]/r"})
    void flagsLinkLocalIncludingCloudMetadata(String url) throws Exception {
        assertThat(GitHostClassifier.classifyGitUrl(url).warnings()).containsExactly("host_link_local");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://10.1.2.3/r", "https://172.16.0.1/r", "https://192.168.1.1/r",
            "https://100.64.0.1/r", "https://[fd12:3456::1]/r"})
    void flagsPrivate(String url) throws Exception {
        assertThat(GitHostClassifier.classifyGitUrl(url).warnings()).containsExactly("host_private");
    }

    @Test
    void publicAddressHasNoWarnings() throws Exception {
        var target = GitHostClassifier.classifyGitUrl("https://8.8.8.8/org/repo");
        assertThat(target.warnings()).isEmpty();
        assertThat(target.classification()).isEqualTo("public");
    }

    @Test
    void unresolvableHostHasNoWarnings() throws Exception {
        assertThat(GitHostClassifier.classifyGitUrl("https://no-such-host.invalid/r").warnings()).isEmpty();
    }

    @Test
    void scpLikeUrlIsTreatedAsSshAndHostIsExtracted() throws Exception {
        var target = GitHostClassifier.classifyGitUrl("git@127.0.0.1:org/repo.git");
        assertThat(target.host()).isEqualTo("127.0.0.1");
        assertThat(target.warnings()).containsExactly("host_loopback");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.com/r", "file:///etc/passwd", "ftp://example.com/r", "/local/path",
            "", "   ", "https://", "https:///org/repo", "ht tp://x"})
    void rejectsUnsupportedOrMalformedUrls(String url) {
        assertThatThrownBy(() -> GitHostClassifier.classifyGitUrl(url))
                .isInstanceOf(GitHostClassifier.RejectedUrlException.class);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> GitHostClassifier.classifyGitUrl(null))
                .isInstanceOf(GitHostClassifier.RejectedUrlException.class);
    }

    @Test
    void apiUrlMustBeHttps() {
        assertThatThrownBy(() -> GitHostClassifier.classifyApiUrl("ssh://example.com"))
                .isInstanceOf(GitHostClassifier.RejectedUrlException.class);
        assertThatThrownBy(() -> GitHostClassifier.classifyApiUrl("git@example.com:org/repo"))
                .isInstanceOf(GitHostClassifier.RejectedUrlException.class);
    }
}
