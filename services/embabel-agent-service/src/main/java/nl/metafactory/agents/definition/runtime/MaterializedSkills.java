package nl.metafactory.agents.definition.runtime;

import com.embabel.agent.api.reference.LlmReference;
import com.embabel.agent.skills.Skills;
import nl.metafactory.agents.definition.skill.SkillPackage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Exposes exactly the run's selected skill packages through Embabel's native {@link Skills}.
 *
 * <p>Embabel reads {@code SKILL.md} and resources lazily from disk. To keep every run on its own
 * immutable snapshot, the in-memory {@link SkillPackage}s are written to a private per-run
 * directory and each one is registered individually with {@link Skills#withLocalSkill(String)} —
 * never a shared root. Scripts are not written at all, and the default
 * {@code NoOpExecutionEngine} is kept, so no script can be read or executed. The directory is
 * deleted on {@link #close()}.
 */
public final class MaterializedSkills implements AutoCloseable {

    private final Path directory;
    private final Skills skills;

    private MaterializedSkills(Path directory, Skills skills) {
        this.directory = directory;
        this.skills = skills;
    }

    public static MaterializedSkills of(String agentId, List<SkillPackage> packages) {
        Path dir = null;
        try {
            dir = Files.createTempDirectory("agent-skills-",
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            var skills = new Skills(agentId + "-skills", "Skills selected for agent " + agentId);
            for (SkillPackage pkg : packages) {
                Path skillDir = Files.createDirectory(dir.resolve(pkg.id()));
                Files.writeString(skillDir.resolve("SKILL.md"), skillMarkdown(pkg));
                for (var resource : pkg.resources().entrySet()) {
                    Path target = skillDir.resolve(resource.getKey()).normalize();
                    if (!target.startsWith(skillDir)) {
                        throw new IllegalStateException("resource path escapes skill directory");
                    }
                    Files.createDirectories(target.getParent());
                    Files.writeString(target, resource.getValue());
                }
                skills = skills.withLocalSkill(skillDir.toString());
            }
            return new MaterializedSkills(dir, skills);
        } catch (IOException e) {
            deleteQuietly(dir);
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            deleteQuietly(dir);
            throw e;
        }
    }

    /** {@code allowed-tools} is deliberately dropped: a skill never grants tools. */
    static String skillMarkdown(SkillPackage pkg) {
        String description = pkg.description().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
        return "---\nname: " + pkg.id() + "\ndescription: \"" + description + "\"\n---\n" + pkg.instructions() + "\n";
    }

    public Skills skills() {
        return skills;
    }

    /** One reference per selected skill plus Embabel's shared resource reader. */
    public List<LlmReference> references() {
        return skills.getSkills().isEmpty() ? List.of() : skills.asIndividualReferences();
    }

    Path directory() {
        return directory;
    }

    @Override
    public void close() {
        deleteQuietly(directory);
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort; the directory is private to this process user
                }
            });
        } catch (IOException ignored) {
            // best effort
        }
    }
}
