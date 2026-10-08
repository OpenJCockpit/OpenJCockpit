package nl.metafactory.aicontrol.repository;

import nl.metafactory.aicontrol.model.ProjectDocumentFolderConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProjectDocumentFolderConfigRepository extends JpaRepository<ProjectDocumentFolderConfig, UUID> {

    Optional<ProjectDocumentFolderConfig> findFirstByProjectIdAndActiveOrderByCreatedAtDesc(UUID projectId, short active);
}
