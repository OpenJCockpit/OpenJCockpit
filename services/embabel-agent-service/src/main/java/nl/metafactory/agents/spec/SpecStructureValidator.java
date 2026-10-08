package nl.metafactory.agents.spec;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Guards the spec-driven folder structure:
 *
 * <pre>
 * specs/
 *   _index.md                 (the only allowed loose file)
 *   NNN-feature-slug/
 *     spec.md  plan.md  tasks.md  review.md
 * </pre>
 *
 * Loose feature files directly under specs/ (e.g. foo-spec.md) are forbidden.
 * Every spec.md must have YAML frontmatter with the required metadata keys.
 */
@Component
public class SpecStructureValidator {

    public static final String INDEX_FILE = "_index.md";
    public static final List<String> REQUIRED_FEATURE_FILES = List.of(
            "spec.md", "plan.md", "tasks.md", "review.md");
    public static final List<String> REQUIRED_FRONTMATTER_KEYS = List.of(
            "workflow_id", "spec_id", "dependencies", "data_profile",
            "llm_strategy", "risk_profile", "validation");

    static final Pattern FEATURE_FOLDER_PATTERN = Pattern.compile("\\d{3}-[a-z0-9]+(?:-[a-z0-9]+)*");

    /** Validates the specs/ folder and returns all violations (empty = valid). */
    public List<String> validate(Path specsDir) {
        List<String> errors = new ArrayList<>();
        if (!Files.isDirectory(specsDir)) {
            errors.add("specs/ folder is missing: " + specsDir);
            return errors;
        }
        try (var entries = Files.list(specsDir)) {
            for (Path entry : entries.sorted().toList()) {
                if (Files.isDirectory(entry)) {
                    validateFeatureFolder(entry, errors);
                } else if (!INDEX_FILE.equals(entry.getFileName().toString())) {
                    errors.add("Loose file directly under specs/ is forbidden (only " + INDEX_FILE
                            + " may live there): specs/" + entry.getFileName()
                            + " — move it into its own feature folder specs/NNN-feature-slug/");
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return errors;
    }

    private void validateFeatureFolder(Path folder, List<String> errors) {
        String name = folder.getFileName().toString();
        if (!FEATURE_FOLDER_PATTERN.matcher(name).matches()) {
            errors.add("Feature folder name does not match the pattern NNN-feature-slug: specs/" + name);
            return;
        }
        for (String required : REQUIRED_FEATURE_FILES) {
            if (!Files.isRegularFile(folder.resolve(required))) {
                errors.add("specs/" + name + " is missing required file: " + required);
            }
        }
        Path specFile = folder.resolve("spec.md");
        if (Files.isRegularFile(specFile)) {
            errors.addAll(validateFrontmatter(specFile, name));
        }
    }

    List<String> validateFrontmatter(Path specFile, String folderName) {
        String content;
        try {
            content = Files.readString(specFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String location = "specs/" + folderName + "/spec.md";
        if (!content.startsWith("---")) {
            return List.of(location + " is missing YAML frontmatter (file must start with ---)");
        }
        int closing = content.indexOf("\n---", 3);
        if (closing < 0) {
            return List.of(location + " has no closing --- for the YAML frontmatter");
        }
        String frontmatter = content.substring(0, closing);
        List<String> errors = new ArrayList<>();
        for (String key : REQUIRED_FRONTMATTER_KEYS) {
            if (!Pattern.compile("(?m)^" + key + ":").matcher(frontmatter).find()) {
                errors.add(location + " frontmatter is missing required key: " + key);
            }
        }
        return errors;
    }
}
