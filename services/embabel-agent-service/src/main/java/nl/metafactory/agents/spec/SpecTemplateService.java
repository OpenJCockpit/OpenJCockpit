package nl.metafactory.agents.spec;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Provides the contents of spec-workflow templates. Templates on disk
 * (templates/specs/ under the base path) take precedence, so a project can
 * customize them; the classpath copy under spec-workflow/ is the fallback and the
 * source from which {@link SpecWorkflowInitializer} creates missing files.
 */
@Component
public class SpecTemplateService {

    public static final String TEMPLATES_DIR = "templates/specs";
    public static final List<String> TEMPLATE_FILES = List.of(
            "feature-spec.template.md",
            "plan.template.md",
            "tasks.template.md",
            "review.template.md");

    private static final String CLASSPATH_ROOT = "spec-workflow/";

    public String templateContent(Path baseDir, String templateName) {
        Path onDisk = baseDir.resolve(TEMPLATES_DIR).resolve(templateName);
        if (Files.isRegularFile(onDisk)) {
            try {
                return Files.readString(onDisk);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return classpathContent(templateName);
    }

    public String classpathContent(String name) {
        try (InputStream in = openResource(name)) {
            if (in == null) {
                throw new IllegalStateException("Spec workflow template resource missing: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    InputStream openResource(String name) {
        return getClass().getClassLoader().getResourceAsStream(CLASSPATH_ROOT + name);
    }

    public String render(String template, Map<String, String> variables) {
        String result = template;
        for (var entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }
}
