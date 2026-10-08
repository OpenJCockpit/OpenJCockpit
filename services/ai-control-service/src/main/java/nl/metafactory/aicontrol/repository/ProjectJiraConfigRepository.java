package nl.metafactory.aicontrol.repository;

import nl.metafactory.aicontrol.model.ProjectJiraConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProjectJiraConfigRepository extends JpaRepository<ProjectJiraConfig, UUID> {

    Optional<ProjectJiraConfig> findFirstByProjectIdAndActiveOrderByCreatedAtDesc(UUID projectId, short active);
}
