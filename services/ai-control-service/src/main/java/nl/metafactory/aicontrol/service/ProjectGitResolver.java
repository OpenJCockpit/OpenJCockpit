package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.config.AgenticWorkflowProperties;
import nl.metafactory.aicontrol.model.GitWorkspaceJobErrorCode;
import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.model.ProjectGitCredential;
import nl.metafactory.aicontrol.repository.ProjectGitCredentialRepository;
import nl.metafactory.aicontrol.repository.ProjectRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Resolves the Git-related facts about a project (validated project, active credential,
 * base branch) shared by the workspace job and spec services.
 */
@Service
public class ProjectGitResolver {

    private final ProjectRepository projectRepo;
    private final ProjectGitCredentialRepository credRepo;
    private final AgenticWorkflowProperties properties;

    public ProjectGitResolver(ProjectRepository projectRepo,
                              ProjectGitCredentialRepository credRepo,
                              AgenticWorkflowProperties properties) {
        this.projectRepo = projectRepo;
        this.credRepo = credRepo;
        this.properties = properties;
    }

    public Project validateProject(UUID projectId) {
        if (projectId == null) {
            throw new GitWorkspaceException(GitWorkspaceJobErrorCode.NO_SELECTED_PROJECT,
                    "No project selected");
        }
        Project project = projectRepo.findById(projectId)
                .orElseThrow(() -> new GitWorkspaceException(
                        GitWorkspaceJobErrorCode.PROJECT_NOT_FOUND,
                        "Project not found: " + projectId));
        if (project.getActive() != 1) {
            throw new GitWorkspaceException(GitWorkspaceJobErrorCode.PROJECT_INACTIVE,
                    "Project is inactive: " + project.getName());
        }
        if (project.getGitUrl() == null || project.getGitUrl().isBlank()) {
            throw new GitWorkspaceException(GitWorkspaceJobErrorCode.GIT_URL_MISSING,
                    "Project has no Git URL: " + project.getName());
        }
        return project;
    }

    public ProjectGitCredential loadCredential(UUID projectId) {
        return credRepo.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(projectId, (short) 1)
                .orElse(null);
    }

    public String resolveBaseBranch(Project project) {
        return project.getDefaultBranch() != null && !project.getDefaultBranch().isBlank()
                ? project.getDefaultBranch()
                : properties.getDefaultBaseBranch();
    }
}
