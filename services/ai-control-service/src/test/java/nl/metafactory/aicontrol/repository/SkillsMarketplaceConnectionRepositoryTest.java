package nl.metafactory.aicontrol.repository;

import nl.metafactory.aicontrol.model.SkillsMarketplaceConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@DataJpaTest
@ActiveProfiles("test")
class SkillsMarketplaceConnectionRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private SkillsMarketplaceConnectionRepository repository;

    @BeforeEach
    void clearData() {
        repository.deleteAll();
        em.flush();
    }

    @Test
    void migrationAppliesAndSaveFindRoundTrips() {
        var saved = repository.saveAndFlush(connection("Acme Skills", "acme skills", "https://marketplace.example.com/api"));
        em.clear();

        var found = repository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Acme Skills");
        assertThat(found.get().getMarketplaceUrl()).isEqualTo("https://marketplace.example.com/api");
    }

    @Test
    void prePersistAndPreUpdateSetTimestamps() {
        var saved = repository.saveAndFlush(connection("Acme Skills", "acme skills", "https://marketplace.example.com/api"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        var firstUpdatedAt = saved.getUpdatedAt();
        saved.setMarketplaceUrl("https://marketplace.example.com/api/v2");
        var updated = repository.saveAndFlush(saved);

        assertThat(updated.getUpdatedAt()).isNotNull();
        assertThat(updated.getCreatedAt()).isEqualTo(saved.getCreatedAt());
        assertThat(firstUpdatedAt).isNotNull();
    }

    @Test
    void findAllByActiveOrderByNameIgnoreCaseOrdersCaseInsensitively() {
        repository.saveAndFlush(connection("acme", "acme", "https://a.example.com"));
        repository.saveAndFlush(connection("Beta", "beta", "https://b.example.com"));
        repository.saveAndFlush(connection("Acme Z", "acme z", "https://c.example.com"));
        em.clear();

        var found = repository.findAllByActiveOrderByNameIgnoreCase((short) 1);

        assertThat(found).extracting(SkillsMarketplaceConnection::getName)
                .containsExactly("acme", "Acme Z", "Beta");
    }

    @Test
    void findAllByActiveOrderByNameIgnoreCaseExcludesInactive() {
        var inactive = connection("Removed", null, "https://removed.example.com");
        inactive.setActive((short) 0);
        repository.saveAndFlush(inactive);
        repository.saveAndFlush(connection("Active", "active", "https://active.example.com"));
        em.clear();

        var found = repository.findAllByActiveOrderByNameIgnoreCase((short) 1);

        assertThat(found).extracting(SkillsMarketplaceConnection::getName).containsExactly("Active");
    }

    @Test
    void uniqueIndexRejectsDuplicateNameKey() {
        repository.saveAndFlush(connection("Acme Skills", "acme skills", "https://a.example.com"));
        var duplicate = connection("ACME SKILLS", "acme skills", "https://b.example.com");

        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void twoNullNameKeyRowsCoexist() {
        var first = connection("First removed", null, "https://a.example.com");
        var second = connection("Second removed", null, "https://b.example.com");

        repository.saveAndFlush(first);
        var savedSecond = repository.saveAndFlush(second);

        assertThat(savedSecond.getId()).isNotNull();
        assertThat(repository.findAll()).hasSize(2);
    }

    @Test
    void findByIdAndActiveIsEmptyForSoftDeletedRow() {
        var saved = repository.saveAndFlush(connection("Acme Skills", "acme skills", "https://a.example.com"));
        saved.setActive((short) 0);
        saved.setNameKey(null);
        repository.saveAndFlush(saved);
        em.clear();

        var found = repository.findByIdAndActive(saved.getId(), (short) 1);

        assertThat(found).isEmpty();
    }

    @Test
    void findByIdAndActiveReturnsActiveRow() {
        var saved = repository.saveAndFlush(connection("Acme Skills", "acme skills", "https://a.example.com"));
        em.clear();

        var found = repository.findByIdAndActive(saved.getId(), (short) 1);

        assertThat(found).isPresent();
    }

    @Test
    void findByNameKeyReturnsMatch() {
        repository.saveAndFlush(connection("Acme Skills", "acme skills", "https://a.example.com"));
        em.clear();

        var found = repository.findByNameKey("acme skills");

        assertThat(found).isPresent();
    }

    private SkillsMarketplaceConnection connection(String name, String nameKey, String url) {
        var c = new SkillsMarketplaceConnection();
        c.setName(name);
        c.setNameKey(nameKey);
        c.setMarketplaceUrl(url);
        c.setEnabled((short) 1);
        c.setActive((short) 1);
        return c;
    }
}
