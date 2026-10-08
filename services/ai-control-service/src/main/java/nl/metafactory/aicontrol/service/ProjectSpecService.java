package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.config.AgenticWorkflowProperties;
import nl.metafactory.aicontrol.model.GitWorkspaceJobErrorCode;
import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.model.ProjectGitCredential;
import nl.metafactory.aicontrol.model.SpecFile;
import nl.metafactory.aicontrol.model.SpecInitResult;
import nl.metafactory.aicontrol.model.SpecInitStatus;
import nl.metafactory.aicontrol.repository.ProjectGitCredentialRepository;
import nl.metafactory.aicontrol.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class ProjectSpecService {

    private static final Logger log = LoggerFactory.getLogger(ProjectSpecService.class);

    static final String SPECS_FOLDER = "specs";
    static final String SPEC_FILENAME = "spec.md";
    static final String INDEX_FILENAME = "_index.md";
    static final String SPECIFY_FOLDER = ".specify";
    static final String TEMPLATE_FOLDER = ".specify/templates";
    static final String SPEC_TEMPLATE_FILENAME = "spec-template.md";
    static final String SPEC_TEMPLATE_RESOURCE = "spec-workflow/spec-template.md";
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ProjectRepository projectRepo;
    private final ProjectGitCredentialRepository credRepo;
    private final GitOperationService gitOps;
    private final BranchNameGenerator branchNameGenerator;
    private final AgenticWorkflowProperties properties;

    public ProjectSpecService(ProjectRepository projectRepo,
                              ProjectGitCredentialRepository credRepo,
                              GitOperationService gitOps,
                              BranchNameGenerator branchNameGenerator,
                              AgenticWorkflowProperties properties) {
        this.projectRepo = projectRepo;
        this.credRepo = credRepo;
        this.gitOps = gitOps;
        this.branchNameGenerator = branchNameGenerator;
        this.properties = properties;
    }

    public List<SpecFile> listSpecFiles(UUID projectId) {
        Project project = validateProject(projectId);
        ProjectGitCredential credential = loadCredential(projectId);
        Path workDir = createWorkDir("spec-list");
        try {
            Path repoDir = workDir.resolve("repo");
            gitOps.cloneDefaultBranch(project.getGitUrl(), resolveBaseBranch(project), repoDir, credential);
            return readSpecFiles(repoDir.resolve(SPECS_FOLDER), project.getGitUrl());
        } finally {
            deleteRecursively(workDir);
        }
    }

    public SpecInitStatus specInitStatus(UUID projectId) {
        Project project = validateProject(projectId);
        ProjectGitCredential credential = loadCredential(projectId);
        // If .specify already exists on the base branch, spec-init (and any still
        // open spec-init PR) is no longer relevant: the dashboard then offers the
        // spec-creation workflow with a prompt instead of the pull-request flow.
        boolean templateExists = specifyFolderExists(project, credential);
        String pendingBranch = findPendingSpecInitBranch(project, credential);
        if (pendingBranch == null) {
            return SpecInitStatus.none(templateExists);
        }
        return new SpecInitStatus(true, pendingBranch,
                branchUrl(project.getGitUrl(), pendingBranch),
                pullRequestUrl(project.getGitUrl(), resolveBaseBranch(project), pendingBranch),
                templateExists);
    }

    private boolean specifyFolderExists(Project project, ProjectGitCredential credential) {
        Path workDir = createWorkDir("spec-status");
        try {
            Path repoDir = workDir.resolve("repo");
            gitOps.cloneDefaultBranch(project.getGitUrl(), resolveBaseBranch(project), repoDir, credential);
            return Files.isDirectory(repoDir.resolve(SPECIFY_FOLDER));
        } finally {
            deleteRecursively(workDir);
        }
    }

    public SpecInitResult initSpecFolder(UUID projectId, String requestedBy) {
        Project project = validateProject(projectId);
        ProjectGitCredential credential = loadCredential(projectId);
        // Anonymously cloning a public repository works, but pushing over https always
        // requires credentials — so fail before cloning with a clear message.
        if (credential == null && project.getGitUrl().startsWith("http")) {
            throw new GitWorkspaceException(GitWorkspaceJobErrorCode.GIT_AUTH_FAILED,
                    "No active git credentials for project " + project.getName()
                    + " — pushing the spec template requires write access to the repository. "
                    + "Configure credentials via Project management.");
        }
        String pendingBranch = findPendingSpecInitBranch(project, credential);
        if (pendingBranch != null) {
            throw new GitWorkspaceException(GitWorkspaceJobErrorCode.NO_CHANGES,
                    "A spec-init branch is already open for project " + project.getName()
                    + ": " + pendingBranch + " — merge the corresponding pull request first.");
        }
        String baseBranch = resolveBaseBranch(project);
        Path workDir = createWorkDir("spec-init");
        try {
            Path repoDir = workDir.resolve("repo");
            gitOps.cloneDefaultBranch(project.getGitUrl(), baseBranch, repoDir, credential);

            Path templateDir = repoDir.resolve(TEMPLATE_FOLDER);
            if (Files.isRegularFile(templateDir.resolve(SPEC_TEMPLATE_FILENAME))) {
                throw new GitWorkspaceException(GitWorkspaceJobErrorCode.NO_CHANGES,
                        "Spec template already exists for project " + project.getName());
            }

            String branch = specInitBranchName(project);
            gitOps.checkoutNewBranch(repoDir, branch);
            writeSpecTemplate(templateDir);
            gitOps.stageAll(repoDir);
            String commitHash = gitOps.commit(repoDir, buildCommitMessage(project, branch, requestedBy),
                    "Agentic Workflow", "agentic@metafactory.nl");
            gitOps.pushBranch(repoDir, "origin", branch, credential);
            log.info("Spec folder initialized for project {} on branch {}", project.getName(), branch);

            return new SpecInitResult(projectId, branch, baseBranch, commitHash, SPEC_TEMPLATE_FILENAME,
                    pullRequestUrl(project.getGitUrl(), baseBranch, branch),
                    "Spec template pushed to branch " + branch + " — open the pull request to add the spec folder.");
        } finally {
            deleteRecursively(workDir);
        }
    }

    public SpecInitResult saveSpecFile(UUID projectId, String fileName, String content, String requestedBy) {
        Project project = validateProject(projectId);
        ProjectGitCredential credential = loadCredential(projectId);
        if (credential == null && project.getGitUrl().startsWith("http")) {
            throw new GitWorkspaceException(GitWorkspaceJobErrorCode.GIT_AUTH_FAILED,
                    "No active git credentials for project " + project.getName()
                    + " — saving the spec requires write access to the repository. "
                    + "Configure credentials via Project management.");
        }
        String baseBranch = resolveBaseBranch(project);
        Path workDir = createWorkDir("spec-save");
        try {
            Path repoDir = workDir.resolve("repo");
            gitOps.cloneDefaultBranch(project.getGitUrl(), baseBranch, repoDir, credential);

            Path targetFile = resolveSpecFilePath(repoDir, fileName);

            String branch = specEditBranchName(project);
            gitOps.checkoutNewBranch(repoDir, branch);
            writeSpecFileContent(targetFile, content);
            gitOps.stageAll(repoDir);
            String commitHash = gitOps.commit(repoDir, buildSaveCommitMessage(project, fileName, branch, requestedBy),
                    "Agentic Workflow", "agentic@metafactory.nl");
            gitOps.pushBranch(repoDir, "origin", branch, credential);
            log.info("Spec file {} saved for project {} on branch {}", fileName, project.getName(), branch);

            return new SpecInitResult(projectId, branch, baseBranch, commitHash, fileName,
                    pullRequestUrl(project.getGitUrl(), baseBranch, branch),
                    "Changes pushed to branch " + branch + " — open the pull request to merge.");
        } finally {
            deleteRecursively(workDir);
        }
    }

    // Guards against path traversal (e.g. "../../.env") from a client-supplied
    // file name: the resolved path must stay within the repo's specs folder and
    // must already exist (this endpoint only edits existing specs, it never creates one).
    private Path resolveSpecFilePath(Path repoDir, String fileName) {
        Path specsDir = repoDir.resolve(SPECS_FOLDER).normalize();
        if (fileName == null || fileName.isBlank()) {
            throw new GitWorkspaceException(GitWorkspaceJobErrorCode.SPEC_FILE_INVALID,
                    "No spec file name provided");
        }
        Path targetFile = specsDir.resolve(fileName).normalize();
        if (!targetFile.startsWith(specsDir) || !Files.isRegularFile(targetFile)) {
            throw new GitWorkspaceException(GitWorkspaceJobErrorCode.SPEC_FILE_INVALID,
                    "Spec file not found: " + fileName);
        }
        return targetFile;
    }

    private void writeSpecFileContent(Path file, String content) {
        try {
            Files.writeString(file, content == null ? "" : content);
        } catch (IOException e) {
            throw new GitWorkspaceException(GitWorkspaceJobErrorCode.SPEC_FILE_INVALID,
                    "Failed to write spec file", e);
        }
    }

    private String buildSaveCommitMessage(Project project, String fileName, String branch, String requestedBy) {
        return "Update spec file " + fileName + " for " + project.getName() + "\n\n" +
               "Edited via the Spec Files Dashboard.\n\n" +
               "Branch:       " + branch + "\n" +
               "Requested-by: " + requestedBy + "\n" +
               "Timestamp:    " + Instant.now() + "\n";
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    Project validateProject(UUID projectId) {
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

    private ProjectGitCredential loadCredential(UUID projectId) {
        return credRepo.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(projectId, (short) 1)
                .orElse(null);
    }

    private String resolveBaseBranch(Project project) {
        return project.getDefaultBranch() != null && !project.getDefaultBranch().isBlank()
                ? project.getDefaultBranch()
                : properties.getDefaultBaseBranch();
    }

    private Path createWorkDir(String prefix) {
        Path dir = Path.of(properties.getWorkspaceBasePath()).resolve(prefix + "-" + UUID.randomUUID());
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return dir;
    }

    // Specs follow the spec-workflow structure: specs/<feature>/spec.md per feature,
    // with _index.md as an overview. Loose markdown files directly under specs/
    // are shown as well, so a simpler repository layout keeps working.
    List<SpecFile> readSpecFiles(Path specsDir, String repositoryUrl) {
        if (!Files.isDirectory(specsDir)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(specsDir)) {
            List<Path> sorted = entries
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            List<SpecFile> result = new ArrayList<>();
            for (Path entry : sorted) {
                String name = entry.getFileName().toString();
                if (Files.isRegularFile(entry) && name.endsWith(".md") && !INDEX_FILENAME.equals(name)) {
                    result.add(buildSpecFile(name.replaceAll("\\.md$", ""), name, entry,
                            result.isEmpty(), repositoryUrl));
                } else if (Files.isDirectory(entry) && Files.isRegularFile(entry.resolve(SPEC_FILENAME))) {
                    result.add(buildSpecFile(name, name + "/" + SPEC_FILENAME, entry.resolve(SPEC_FILENAME),
                            result.isEmpty(), repositoryUrl));
                }
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private SpecFile buildSpecFile(String id, String fileName, Path file, boolean selected,
                                   String repositoryUrl) throws IOException {
        String content = Files.readString(file);
        String lastChanged = formatDate(Files.getLastModifiedTime(file).toInstant());
        return new SpecFile(id, fileName, "", lastChanged, selected ? "Active" : "Open",
                selected, content, repositoryUrl);
    }

    private static String formatDate(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("d MMM HH:mm"));
    }

    String specInitBranchPrefix(Project project) {
        return "spec-init/" + branchNameGenerator.toSlug(project.getName()) + "/";
    }

    String specInitBranchName(Project project) {
        String timestamp = TIMESTAMP.format(LocalDateTime.now(ZoneOffset.UTC));
        String shortId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return specInitBranchPrefix(project) + timestamp + "-" + shortId;
    }

    String specEditBranchPrefix(Project project) {
        return "spec-edit/" + branchNameGenerator.toSlug(project.getName()) + "/";
    }

    String specEditBranchName(Project project) {
        String timestamp = TIMESTAMP.format(LocalDateTime.now(ZoneOffset.UTC));
        String shortId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return specEditBranchPrefix(project) + timestamp + "-" + shortId;
    }

    // The naming contains a timestamp, so the lexicographically greatest branch is the most recent.
    String findPendingSpecInitBranch(Project project, ProjectGitCredential credential) {
        String prefix = specInitBranchPrefix(project);
        return gitOps.listRemoteBranches(project.getGitUrl(), credential).stream()
                .filter(branch -> branch.startsWith(prefix))
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    String branchUrl(String gitUrl, String branch) {
        if (gitUrl == null || !gitUrl.startsWith("http")) {
            return null;
        }
        String base = gitUrl.endsWith(".git") ? gitUrl.substring(0, gitUrl.length() - 4) : gitUrl;
        return base + "/tree/" + branch;
    }

    private void writeSpecTemplate(Path specDir) {
        try {
            Files.createDirectories(specDir);
            Files.writeString(specDir.resolve(SPEC_TEMPLATE_FILENAME), specTemplateContent());
        } catch (IOException e) {
            throw new GitWorkspaceException(GitWorkspaceJobErrorCode.SPEC_FILE_INVALID,
                    "Failed to write spec template", e);
        }
    }

    String specTemplateContent() {
        try (InputStream in = openSpecTemplateResource()) {
            if (in == null) {
                throw new GitWorkspaceException(GitWorkspaceJobErrorCode.SPEC_FILE_INVALID,
                        "Spec template resource missing: " + SPEC_TEMPLATE_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new GitWorkspaceException(GitWorkspaceJobErrorCode.SPEC_FILE_INVALID,
                    "Failed to read spec template", e);
        }
    }

    InputStream openSpecTemplateResource() {
        return getClass().getClassLoader().getResourceAsStream(SPEC_TEMPLATE_RESOURCE);
    }

    private String buildCommitMessage(Project project, String branch, String requestedBy) {
        return "Initialize " + TEMPLATE_FOLDER + " folder for " + project.getName() + "\n\n" +
               "Adds the spec template so agentic workflows can be executed against this repository.\n\n" +
               "Branch:       " + branch + "\n" +
               "Requested-by: " + requestedBy + "\n" +
               "Timestamp:    " + Instant.now() + "\n";
    }

    String pullRequestUrl(String gitUrl, String baseBranch, String branch) {
        if (gitUrl == null || !gitUrl.startsWith("http")) {
            return null;
        }
        String base = gitUrl.endsWith(".git") ? gitUrl.substring(0, gitUrl.length() - 4) : gitUrl;
        return base + "/compare/" + baseBranch + "..." + branch + "?expand=1";
    }

    private void deleteRecursively(Path dir) {
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    Files.delete(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            log.warn("Failed to clean up spec workspace for {}: {}", dir, e.getMessage());
        }
    }
}
