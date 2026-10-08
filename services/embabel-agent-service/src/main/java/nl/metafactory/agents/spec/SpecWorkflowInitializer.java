package nl.metafactory.agents.spec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Initializes the spec-driven workflow structure at startup:
 *
 * <ol>
 *   <li>creates templates/specs/ with the template files if they are missing;</li>
 *   <li>creates AGENTS.md with the workflow rules, or appends the rules to an
 *       existing AGENTS.md without duplicating them;</li>
 *   <li>creates specs/ with _index.md and the example feature if specs/ does not exist yet;</li>
 *   <li>then validates the structure and fails hard (with clear errors) on, among other things,
 *       loose spec files directly under specs/.</li>
 * </ol>
 *
 * An existing specs/ folder is never silently migrated: loose files
 * lead to a validation error that the user must resolve themselves.
 */
@Component
public class SpecWorkflowInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SpecWorkflowInitializer.class);

    static final String AGENTS_FILE = "AGENTS.md";
    static final String AGENTS_RULES_MARKER = "# Spec-driven workflow rules";
    static final String AGENTS_RULES_RESOURCE = "agents-rules.md";
    static final String INDEX_RESOURCE = "_index.md";
    static final String EXAMPLE_FEATURE_FOLDER = "001-example-feature";
    static final String EXAMPLE_FEATURE_NAME = "Example Feature";

    private final SpecWorkflowProperties properties;
    private final SpecTemplateService templates;
    private final SpecStructureValidator validator;

    public SpecWorkflowInitializer(SpecWorkflowProperties properties,
                                   SpecTemplateService templates,
                                   SpecStructureValidator validator) {
        this.properties = properties;
        this.templates = templates;
        this.validator = validator;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isInitEnabled()) {
            log.info("Spec workflow initialization is disabled (openjcockpit.spec-workflow.init-enabled=false)");
            return;
        }
        initialize(Path.of(properties.getBasePath()));
    }

    public void initialize(Path baseDir) {
        try {
            ensureTemplates(baseDir);
            ensureAgentsRules(baseDir);
            ensureSpecsStructure(baseDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<String> errors = validator.validate(baseDir.resolve("specs"));
        if (!errors.isEmpty()) {
            errors.forEach(error -> log.error("Spec structure validation: {}", error));
            throw new IllegalStateException("Spec workflow validation failed:\n- " + String.join("\n- ", errors));
        }
        log.info("Spec workflow structure validated: {}", baseDir.resolve("specs"));
    }

    private void ensureTemplates(Path baseDir) throws IOException {
        Path templatesDir = baseDir.resolve(SpecTemplateService.TEMPLATES_DIR);
        Files.createDirectories(templatesDir);
        for (String templateName : SpecTemplateService.TEMPLATE_FILES) {
            Path file = templatesDir.resolve(templateName);
            if (!Files.exists(file)) {
                Files.writeString(file, templates.classpathContent(templateName));
                log.info("Spec template created: {}", file);
            }
        }
    }

    private void ensureAgentsRules(Path baseDir) throws IOException {
        Path agentsFile = baseDir.resolve(AGENTS_FILE);
        String rules = templates.classpathContent(AGENTS_RULES_RESOURCE);
        if (!Files.exists(agentsFile)) {
            Files.writeString(agentsFile, rules);
            log.info("AGENTS.md created with spec-driven workflow rules");
            return;
        }
        String existing = Files.readString(agentsFile);
        if (existing.contains(AGENTS_RULES_MARKER)) {
            return;
        }
        String separator = existing.endsWith("\n") ? "\n" : "\n\n";
        Files.writeString(agentsFile, existing + separator + rules);
        log.info("Spec-driven workflow rules appended to existing AGENTS.md");
    }

    private void ensureSpecsStructure(Path baseDir) throws IOException {
        Path specsDir = baseDir.resolve("specs");
        if (Files.isDirectory(specsDir)) {
            return;
        }
        Path featureDir = specsDir.resolve(EXAMPLE_FEATURE_FOLDER);
        Files.createDirectories(featureDir);
        Files.writeString(specsDir.resolve(SpecStructureValidator.INDEX_FILE),
                templates.classpathContent(INDEX_RESOURCE));

        Map<String, String> variables = Map.of("feature_name", EXAMPLE_FEATURE_NAME);
        Map<String, String> featureFileByTemplate = Map.of(
                "feature-spec.template.md", "spec.md",
                "plan.template.md", "plan.md",
                "tasks.template.md", "tasks.md",
                "review.template.md", "review.md");
        for (var entry : featureFileByTemplate.entrySet()) {
            String content = templates.render(templates.templateContent(baseDir, entry.getKey()), variables);
            Files.writeString(featureDir.resolve(entry.getValue()), content);
        }
        log.info("Spec structure initialized with example feature: {}", featureDir);
    }
}
