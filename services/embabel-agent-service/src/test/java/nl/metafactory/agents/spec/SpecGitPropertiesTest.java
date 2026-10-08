package nl.metafactory.agents.spec;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpecGitPropertiesTest {

    @Test
    void enabledDefaultsToTrueAndCanBeChanged() {
        var properties = new SpecGitProperties();

        assertThat(properties.isEnabled()).isTrue();

        properties.setEnabled(false);
        assertThat(properties.isEnabled()).isFalse();
    }

    @Test
    void branchAndDirectoryDefaultsCanBeChanged() {
        var properties = new SpecGitProperties();

        assertThat(properties.getBaseBranch()).isEqualTo("main");
        assertThat(properties.getBranchPrefix()).isEqualTo("spec");
        assertThat(properties.getImplBranchPrefix()).isEqualTo("impl");
        assertThat(properties.getRealisationBranchPrefix()).isEqualTo("feat");
        assertThat(properties.getSpecDirectory()).isEqualTo("specs");

        properties.setBaseBranch("develop");
        properties.setBranchPrefix("agentic");
        properties.setImplBranchPrefix("implementation");
        properties.setRealisationBranchPrefix("realisation");
        properties.setSpecDirectory("docs/specs");
        assertThat(properties.getBaseBranch()).isEqualTo("develop");
        assertThat(properties.getBranchPrefix()).isEqualTo("agentic");
        assertThat(properties.getImplBranchPrefix()).isEqualTo("implementation");
        assertThat(properties.getRealisationBranchPrefix()).isEqualTo("realisation");
        assertThat(properties.getSpecDirectory()).isEqualTo("docs/specs");
    }

    @Test
    void credentialsDefaultToAnonymousAndCanBeChanged() {
        var properties = new SpecGitProperties();

        assertThat(properties.getUsername()).isEmpty();
        assertThat(properties.getToken()).isEmpty();

        properties.setUsername("bot");
        properties.setToken("secret");
        assertThat(properties.getUsername()).isEqualTo("bot");
        assertThat(properties.getToken()).isEqualTo("secret");
    }

    @Test
    void authorDefaultsCanBeChanged() {
        var properties = new SpecGitProperties();

        assertThat(properties.getAuthorName()).isEqualTo("Agentic Workflow");
        assertThat(properties.getAuthorEmail()).isEqualTo("agentic@metafactory.nl");

        properties.setAuthorName("Spec Bot");
        properties.setAuthorEmail("bot@example.com");
        assertThat(properties.getAuthorName()).isEqualTo("Spec Bot");
        assertThat(properties.getAuthorEmail()).isEqualTo("bot@example.com");
    }
}
