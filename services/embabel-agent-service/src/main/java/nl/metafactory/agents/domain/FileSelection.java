package nl.metafactory.agents.domain;

import java.util.List;

/** Files the realisation agent wants to read as context. */
public record FileSelection(List<String> paths) {}
