package nl.metafactory.aicontrol.repository;

import nl.metafactory.aicontrol.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findAllByActiveOrderByNameAsc(short active);

    List<Project> findAllByOrderByNameAsc();
}
