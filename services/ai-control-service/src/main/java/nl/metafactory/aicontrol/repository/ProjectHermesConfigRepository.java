package nl.metafactory.aicontrol.repository;

import nl.metafactory.aicontrol.model.ProjectHermesConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProjectHermesConfigRepository extends JpaRepository<ProjectHermesConfig, UUID> {

    Optional<ProjectHermesConfig> findFirstByProjectIdAndActiveOrderByCreatedAtDesc(UUID projectId, short active);
}
