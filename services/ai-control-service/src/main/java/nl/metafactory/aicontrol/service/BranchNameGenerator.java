package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.config.AgenticWorkflowProperties;
import nl.metafactory.aicontrol.model.Project;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class BranchNameGenerator {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int MAX_BRANCH_LENGTH = 200;

    private final AgenticWorkflowProperties properties;

    public BranchNameGenerator(AgenticWorkflowProperties properties) {
        this.properties = properties;
    }

    public String generate(Project project, UUID jobId) {
        String timestamp = TIMESTAMP.format(LocalDateTime.now(ZoneOffset.UTC));
        String shortId = jobId.toString().replace("-", "").substring(0, 8);
        String slug = toSlug(project.getName());
        String branch = properties.getBranchPrefix() + "/" + slug + "/" + timestamp + "-" + shortId;
        return branch.length() > MAX_BRANCH_LENGTH ? branch.substring(0, MAX_BRANCH_LENGTH) : branch;
    }

    String toSlug(String name) {
        if (name == null || name.isBlank()) return "project";
        return name.toLowerCase()
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}