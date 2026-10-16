package nl.metafactory.agents.definition.litellm;

import nl.metafactory.agents.definition.markdown.AgentMarkdownParser;
import nl.metafactory.agents.definition.model.DefinitionSource;
import nl.metafactory.agents.definition.model.SourceStatus;
import nl.metafactory.agents.definition.resolver.FallbackPolicy;
import nl.metafactory.agents.definition.resolver.SourceChainSelector;
import nl.metafactory.agents.definition.binding.AgentTenantBinding;
import nl.metafactory.agents.definition.skill.LocalSkillInventory;
import nl.metafactory.agents.definition.skill.SkillMarkdownParser;
import nl.metafactory.agents.definition.skill.SkillResolver;
import nl.metafactory.agents.definition.source.ConfinedRoot;
import nl.metafactory.agents.definition.source.LocalMarkdownAgentDefinitionProvider;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalContentProvidersTest {

    static final ConfinedRoot ROOT = new ConfinedRoot(Path.of("src/test/resources/agent-definitions"));

    @Test
    void externalAgentContentIsExplicitlyUnsupported() {
        var result = new ExternalAgentContentProvider().lookup("customer-support");
        assertThat(result.status()).isEqualTo(SourceStatus.UNSUPPORTED);
        assertThat(result.document()).isNull();
        assertThat(result.reason()).contains("v1.101.2");
    }

    @Test
    void externalSkillsAreExplicitlyUnsupported() {
        var result = new ExternalSkillPackageProvider().lookup("customer-lookup");
        assertThat(result.status()).isEqualTo(SourceStatus.UNSUPPORTED);
        assertThat(result.skill()).isNull();
    }

    @Test
    void localAgentStillResolvesBehindTheUnsupportedRemote() {
        var selector = new SourceChainSelector(List.of(new ExternalAgentContentProvider(),
                new LocalMarkdownAgentDefinitionProvider(ROOT, new AgentMarkdownParser())), FallbackPolicy.NONE);
        var selection = selector.select(new AgentTenantBinding("t", "customer-support", true, null, null, false,
                false, Set.of(), Set.of()));

        assertThat(selection.source()).isEqualTo(DefinitionSource.LOCAL);
        assertThat(selection.attempts()).extracting(a -> a.status())
                .containsExactly(SourceStatus.UNSUPPORTED, SourceStatus.FOUND);
    }

    @Test
    void localSkillsStillResolveBehindTheUnsupportedRemote() {
        var resolver = new SkillResolver(List.of(new ExternalSkillPackageProvider(),
                new LocalSkillInventory(ROOT, new SkillMarkdownParser())));
        var doc = new AgentMarkdownParser().parse(
                "---\nid: a\nversion: '1'\nmodel: m\nskills: [customer-lookup]\n---\nbody\n");
        assertThat(resolver.resolve(doc).skills()).hasSize(1);
    }
}
