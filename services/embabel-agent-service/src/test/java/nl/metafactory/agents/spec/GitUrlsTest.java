package nl.metafactory.agents.spec;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GitUrlsTest {

    @Test
    void compareUrlIsOnlyBuiltForHttpUrls() {
        assertThat(GitUrls.compareUrl("https://github.com/org/repo.git", "spec/x"))
                .isEqualTo("https://github.com/org/repo/compare/spec/x?expand=1");
        assertThat(GitUrls.compareUrl("https://github.com/org/repo", "spec/x"))
                .isEqualTo("https://github.com/org/repo/compare/spec/x?expand=1");
        assertThat(GitUrls.compareUrl("file:///tmp/repo.git", "spec/x")).isNull();
        assertThat(GitUrls.compareUrl(null, "spec/x")).isNull();
    }
}
