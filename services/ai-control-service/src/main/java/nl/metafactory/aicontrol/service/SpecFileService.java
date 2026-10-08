package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.model.SpecFile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class SpecFileService {

    private final SpecRepositoryClient specClient;

    public SpecFileService(SpecRepositoryClient specClient) {
        this.specClient = specClient;
    }

    public SpecFile resolveSpecFile(String specFileRef) {
        List<SpecFile> allSpecs = specClient.listSpecs("*");
        return allSpecs.stream()
                .filter(s -> specFileRef.equals(s.id()) || specFileRef.equals(s.fileName()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Spec file not found: " + specFileRef));
    }

    public void validateSpecFile(SpecFile spec) {
        if (spec.content() == null || spec.content().isBlank()) {
            throw new GitWorkspaceException(
                    nl.metafactory.aicontrol.model.GitWorkspaceJobErrorCode.SPEC_FILE_INVALID,
                    "Spec file is empty: " + spec.fileName());
        }
    }

    public void writeSpecToWorkspace(SpecFile spec, Path specDir) throws IOException {
        Files.createDirectories(specDir);
        Files.writeString(specDir.resolve("spec.md"), spec.content());
    }
}