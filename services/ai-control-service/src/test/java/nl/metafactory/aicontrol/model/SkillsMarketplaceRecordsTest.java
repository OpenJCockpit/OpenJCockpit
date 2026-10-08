package nl.metafactory.aicontrol.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SkillsMarketplaceRecordsTest {

    @Test
    void apiErrorResponse() {
        var r = new ApiErrorResponse("NAME_CONFLICT", "A connection with this name already exists");
        assertThat(r.code()).isEqualTo("NAME_CONFLICT");
        assertThat(r.message()).isEqualTo("A connection with this name already exists");
    }

    @Test
    void skillsMarketplaceConnectionDto() {
        var id = UUID.randomUUID();
        var now = Instant.now();
        var r = new SkillsMarketplaceConnectionDto(id, "Acme Skills", "https://marketplace.example.com/api",
                "An example marketplace", true, true, "ricky", now, now);

        assertThat(r.id()).isEqualTo(id);
        assertThat(r.name()).isEqualTo("Acme Skills");
        assertThat(r.marketplaceUrl()).isEqualTo("https://marketplace.example.com/api");
        assertThat(r.description()).isEqualTo("An example marketplace");
        assertThat(r.enabled()).isTrue();
        assertThat(r.hasApiKey()).isTrue();
        assertThat(r.createdBy()).isEqualTo("ricky");
        assertThat(r.createdAt()).isEqualTo(now);
        assertThat(r.updatedAt()).isEqualTo(now);
    }

    @Test
    void skillsMarketplaceConnectionRequest() {
        var r = new SkillsMarketplaceConnectionRequest("Acme Skills", "https://marketplace.example.com/api",
                "secret-key-value", "An example marketplace", true);

        assertThat(r.name()).isEqualTo("Acme Skills");
        assertThat(r.marketplaceUrl()).isEqualTo("https://marketplace.example.com/api");
        assertThat(r.apiKey()).isEqualTo("secret-key-value");
        assertThat(r.description()).isEqualTo("An example marketplace");
        assertThat(r.enabled()).isTrue();
    }

    @Test
    void skillsMarketplaceConnectionEntityGettersAndSetters() {
        var entity = new SkillsMarketplaceConnection();
        var id = UUID.randomUUID();
        var now = Instant.now();

        entity.setId(id);
        entity.setName("Acme Skills");
        entity.setNameKey("acme skills");
        entity.setMarketplaceUrl("https://marketplace.example.com/api");
        entity.setEncryptedApiKey("cipher");
        entity.setDescription("An example marketplace");
        entity.setEnabled((short) 1);
        entity.setActive((short) 1);
        entity.setCreatedBy("ricky");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getName()).isEqualTo("Acme Skills");
        assertThat(entity.getNameKey()).isEqualTo("acme skills");
        assertThat(entity.getMarketplaceUrl()).isEqualTo("https://marketplace.example.com/api");
        assertThat(entity.getEncryptedApiKey()).isEqualTo("cipher");
        assertThat(entity.getDescription()).isEqualTo("An example marketplace");
        assertThat(entity.getEnabled()).isEqualTo((short) 1);
        assertThat(entity.getActive()).isEqualTo((short) 1);
        assertThat(entity.getCreatedBy()).isEqualTo("ricky");
        assertThat(entity.getCreatedAt()).isEqualTo(now);
        assertThat(entity.getUpdatedAt()).isEqualTo(now);

        entity.prePersist();
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isEqualTo(entity.getCreatedAt());
        entity.preUpdate();
        assertThat(entity.getUpdatedAt()).isNotNull();
    }
}
