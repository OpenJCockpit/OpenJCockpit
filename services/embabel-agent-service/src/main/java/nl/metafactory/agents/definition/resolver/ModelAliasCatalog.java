package nl.metafactory.agents.definition.resolver;

import java.util.Set;

/**
 * Model aliases a definition may use: the Embabel LLM roles configured under
 * {@code embabel.models.llms}. An alias outside this set is rejected at resolution time, so a
 * definition can never select an arbitrary model name.
 */
public record ModelAliasCatalog(Set<String> aliases) {

    public ModelAliasCatalog {
        aliases = Set.copyOf(aliases);
    }

    public boolean contains(String alias) {
        return aliases.contains(alias);
    }
}
