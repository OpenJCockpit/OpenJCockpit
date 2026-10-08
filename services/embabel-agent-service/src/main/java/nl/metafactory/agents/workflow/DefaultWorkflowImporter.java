package nl.metafactory.agents.workflow;

import nl.metafactory.agents.config.WorkflowDefinitionProperties;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import nl.metafactory.agents.workflow.model.WorkflowExportBundle;
import nl.metafactory.agents.workflow.model.WorkflowGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Imports the default workflow bundle (spec folder
 * initialization, spec creation from prompt + RAG, and spec implementation) from
 * {@value #DEFAULT_BUNDLE_RESOURCE} at startup. Existing records with the same id are
 * never overwritten, so user customizations are preserved.
 */
@Component
@Order(10)
public class DefaultWorkflowImporter implements ApplicationRunner {

    static final String DEFAULT_BUNDLE_RESOURCE = "spec-workflow/default-workflows.json";

    private static final Logger log = LoggerFactory.getLogger(DefaultWorkflowImporter.class);

    private final WorkflowDefinitionProperties properties;
    private final WorkflowDefinitionRepository workflowRepository;
    private final WorkflowGroupRepository groupRepository;
    private final DefaultWorkflowBundleReader bundleReader;

    public DefaultWorkflowImporter(WorkflowDefinitionProperties properties,
                                   WorkflowDefinitionRepository workflowRepository,
                                   WorkflowGroupRepository groupRepository, DefaultWorkflowBundleReader bundleReader) {
        this.properties = properties;
        this.workflowRepository = workflowRepository;
        this.groupRepository = groupRepository;
        this.bundleReader = bundleReader;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isSeedDefaults()) {
            log.info("Default workflow import is disabled (metafactory.workflow-definitions.seed-defaults=false)");
            return;
        }
        importDefaults();
    }

    public void importDefaults() {
        WorkflowExportBundle bundle = readBundle();
        List<WorkflowGroup> groups = bundle.groups() != null ? bundle.groups() : List.of();
        List<WorkflowDefinition> workflows = bundle.workflows() != null ? bundle.workflows() : List.of();

        int importedGroups = 0;
        for (WorkflowGroup group : groups) {
            if (groupRepository.findById(group.id()).isEmpty()) {
                groupRepository.save(group);
                importedGroups++;
            }
        }
        int importedWorkflows = 0;
        for (WorkflowDefinition workflow : workflows) {
            if (workflowRepository.findById(workflow.id()).isEmpty()) {
                workflowRepository.save(workflow);
                importedWorkflows++;
            }
        }
        log.info("Default workflow bundle imported: {} group(s) and {} workflow(s) added, existing records left untouched",
                importedGroups, importedWorkflows);
    }

    WorkflowExportBundle readBundle() { return bundleReader.readBundle(); }

}
