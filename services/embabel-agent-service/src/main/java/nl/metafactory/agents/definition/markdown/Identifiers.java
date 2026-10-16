package nl.metafactory.agents.definition.markdown;

import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

/** Allow-list patterns for every identifier that may end up in a path or a policy lookup. */
public final class Identifiers {

    /** Agent and skill ids: lowercase kebab-case, also safe as a single path segment. */
    public static final Pattern SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    /** Tool and guardrail ids may additionally use dots and underscores as separators. */
    public static final Pattern QUALIFIED = Pattern.compile("[a-z0-9]+(?:[._-][a-z0-9]+)*");

    public static final int MAX_LENGTH = 64;

    private Identifiers() {
    }

    public static boolean isSlug(String value) {
        return value != null && value.length() <= MAX_LENGTH && SLUG.matcher(value).matches();
    }

    public static String requireSlug(String value, String field) {
        if (!isSlug(value)) {
            throw new InvalidDefinitionException(field + " must be lowercase kebab-case (max " + MAX_LENGTH + " chars)");
        }
        return value;
    }

    public static List<String> requireIds(List<String> values, Pattern pattern, String field) {
        if (values == null) {
            return List.of();
        }
        var seen = new HashSet<String>();
        for (String value : values) {
            if (value == null || value.length() > MAX_LENGTH || !pattern.matcher(value).matches()) {
                throw new InvalidDefinitionException(field + " contains an invalid identifier");
            }
            if (!seen.add(value)) {
                throw new InvalidDefinitionException(field + " contains duplicate identifier " + value);
            }
        }
        return List.copyOf(values);
    }
}
