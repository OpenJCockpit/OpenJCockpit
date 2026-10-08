package nl.metafactory.aicontrol.repository;

import nl.metafactory.aicontrol.model.SkillsMarketplaceConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SkillsMarketplaceConnectionRepository extends JpaRepository<SkillsMarketplaceConnection, UUID> {

    // A derived `OrderByNameAsc` would be collation-dependent in Postgres and therefore
    // non-deterministic for BR-6; JPQL `lower(...)` is portable and verified on both H2 and
    // Postgres (see ADR-2).
    @Query("select c from SkillsMarketplaceConnection c where c.active = :active order by lower(c.name) asc")
    List<SkillsMarketplaceConnection> findAllByActiveOrderByNameIgnoreCase(@Param("active") short active);

    // BR-1: only active AND enabled connections are ever queried for the merged skill catalog.
    @Query("select c from SkillsMarketplaceConnection c where c.active = :active and c.enabled = :enabled "
            + "order by lower(c.name) asc")
    List<SkillsMarketplaceConnection> findAllByActiveAndEnabledOrderByNameIgnoreCase(
            @Param("active") short active, @Param("enabled") short enabled);

    Optional<SkillsMarketplaceConnection> findByIdAndActive(UUID id, short active);

    Optional<SkillsMarketplaceConnection> findByNameKey(String nameKey);
}
