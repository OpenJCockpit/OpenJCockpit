package nl.metafactory.aicontrol.repository;

import nl.metafactory.aicontrol.model.ProjectGitCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProjectGitCredentialRepository extends JpaRepository<ProjectGitCredential, UUID> {

    Optional<ProjectGitCredential> findFirstByProjectIdAndActiveOrderByCreatedAtDesc(UUID projectId, short active);
}