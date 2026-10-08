package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.model.SkillsMarketplaceConnectionRequest;
import nl.metafactory.aicontrol.repository.SkillsMarketplaceConnectionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for QA-1: {@code update()} must return a DTO whose {@code updatedAt}
 * already reflects the real, persisted timestamp — not a stale pre-flush value.
 *
 * <p>Unlike {@link SkillsMarketplaceConnectionServiceTest} (Mockito repository, no real JPA
 * lifecycle callbacks) and {@link nl.metafactory.aicontrol.repository.SkillsMarketplaceConnectionRepositoryTest}
 * (which uses {@code saveAndFlush} directly, forcing the flush before the assertion and thereby
 * masking the timing bug), this test wires up a real persistence context and calls the service's
 * actual {@link SkillsMarketplaceConnectionService#update(java.util.UUID,
 * SkillsMarketplaceConnectionRequest, String)} method — the same code path used by the
 * {@code PUT /api/skills-marketplaces/{id}} controller — and asserts on the DTO it returns
 * <em>immediately</em>, before any subsequent flush or re-fetch could paper over a stale value.
 *
 * <p>Before the fix, {@code updatedAt} on this entity was set exclusively by the
 * {@code @PreUpdate} JPA lifecycle callback, which only fires at flush time. Because
 * {@code @Transactional} service methods flush at commit — i.e. after the method has already
 * built and returned its DTO from the in-memory entity — the returned DTO carried the entity's
 * pre-flush (stale) {@code updatedAt}. This test would have failed against that code.</p>
 */
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@DataJpaTest
@Import({SkillsMarketplaceConnectionService.class, CredentialEncryptionService.class})
@ActiveProfiles("test")
class SkillsMarketplaceConnectionServiceJpaTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private SkillsMarketplaceConnectionRepository repository;

    @Autowired
    private SkillsMarketplaceConnectionService service;

    @Test
    void updateReturnsDtoWithFreshUpdatedAtWithoutAnyExplicitFlushOrRefetch() {
        var createRequest = new SkillsMarketplaceConnectionRequest(
                "Acme Skills", "https://marketplace.example.com/api", "secret-key-value", "desc", true);
        var created = service.create(createRequest, "ricky");
        // Force the *create* flush only, so createdAt/updatedAt are settled on the row before we
        // measure the update. This mirrors "create at T0, then PUT later" from the QA repro.
        em.flush();
        em.clear();

        var beforeUpdate = repository.findById(created.id()).orElseThrow().getUpdatedAt();

        var updateRequest = new SkillsMarketplaceConnectionRequest(
                "Acme Skills", "https://marketplace.example.com/api/v2", null, "desc", true);
        // Deliberately no em.flush()/em.clear() between create and this call: the whole point is
        // to observe exactly what update() returns from its own in-memory entity, with the same
        // timing a real @Transactional controller call would have (flush happens at commit, which
        // this test never reaches).
        var updated = service.update(created.id(), updateRequest, "ricky");

        assertThat(updated.updatedAt())
                .as("DTO returned immediately by update() must already carry the fresh updatedAt")
                .isNotNull()
                .isAfter(beforeUpdate);
        assertThat(updated.marketplaceUrl()).isEqualTo("https://marketplace.example.com/api/v2");
    }
}
