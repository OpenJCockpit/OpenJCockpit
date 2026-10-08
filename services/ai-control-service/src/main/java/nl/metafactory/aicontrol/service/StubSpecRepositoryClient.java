package nl.metafactory.aicontrol.service;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.aicontrol.model.SpecFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class StubSpecRepositoryClient implements SpecRepositoryClient {

    private final File specRepositoryRoot;

    public StubSpecRepositoryClient(
            @Value("${openjcockpit.spec-repository.path:../../spec-repository-stub}") String path) {
        this.specRepositoryRoot = new File(path);
    }

    @Override
    public List<SpecFile> listSpecs(String customerId) {
        File specsDir = new File(specRepositoryRoot, "customers/" + customerId + "/specs");
        File[] mdFiles = specsDir.listFiles((dir, name) -> name.endsWith(".md"));
        if (mdFiles == null || mdFiles.length == 0) {
            return List.of();
        }
        Arrays.sort(mdFiles, Comparator.comparing(File::getName));
        Map<String, String> repoMap = loadRepoMap(customerId);
        try {
            List<SpecFile> result = new ArrayList<>();
            for (int i = 0; i < mdFiles.length; i++) {
                result.add(buildSpecFile(mdFiles[i], i == 0, repoMap));
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Map<String, String> loadRepoMap(String customerId) {
        File reposFile = new File(specRepositoryRoot, "customers/" + customerId + "/repos.json");
        if (!reposFile.exists()) return Map.of();
        try {
            return JsonMapper.builder().build().readValue(reposFile, new TypeReference<>() {});
        } catch (JacksonException e) {
            return Map.of();
        }
    }

    private SpecFile buildSpecFile(File file, boolean selected, Map<String, String> repoMap) throws IOException {
        String id = file.getName().replaceAll("\\.md$", "");
        String content = Files.readString(file.toPath());
        String lastChanged = formatDate(file.lastModified());
        String repositoryUrl = repoMap.getOrDefault(file.getName(), "");
        return new SpecFile(id, file.getName(), "", lastChanged, selected ? "Active" : "Open", selected, content, repositoryUrl);
    }

    private static String formatDate(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH));
    }
}
