package nl.metafactory.agents.security;

import java.util.Optional;

/** Resolves the username of the currently authenticated caller, if any. Never fabricates a
 * placeholder value (e.g. "unknown") — absence of an authenticated principal is represented as
 * {@link Optional#empty()} so callers can distinguish "no identity available" from a real value. */
public interface CurrentUserProvider {

    Optional<String> currentUsername();
}
