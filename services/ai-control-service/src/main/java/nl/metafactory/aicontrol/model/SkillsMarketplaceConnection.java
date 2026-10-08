package nl.metafactory.aicontrol.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A registered connection to an external skills marketplace. Global (not per-project) — see
 * ADR-1 in the skills-marketplace-settings architecture doc. As of the
 * skills-tab-marketplace-import delivery, every active+enabled row IS queried at read time by
 * {@code SkillCatalogService} through the swappable {@code SkillsMarketplaceClient} adapter seam
 * (architecture ADR-1). This entity itself remains pure registration metadata — nothing is ever
 * written back to it as a result of that querying (BR-12), and no new persistent state is created.
 *
 * <p>Mirrors {@code V11__create_skills_marketplace_connections.sql} exactly (column names,
 * nullability, lengths) because {@code spring.jpa.hibernate.ddl-auto} is {@code validate}. Do
 * not add {@code @Table(uniqueConstraints = ...)} — the unique index on {@code name_key} is a
 * plain database index that Hibernate's validator does not inspect (see ADR-2).</p>
 *
 * <p>Deliberately has no {@code toString()} so a future maintainer does not add one that
 * echoes {@code encryptedApiKey}.</p>
 */
@Entity
@Table(name = "skills_marketplace_connections")
public class SkillsMarketplaceConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "name_key", length = 100)
    private String nameKey;

    @Column(name = "marketplace_url", nullable = false, length = 500)
    private String marketplaceUrl;

    @Column(name = "encrypted_api_key", columnDefinition = "TEXT")
    private String encryptedApiKey;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private short enabled = 1;

    @Column(nullable = false)
    private short active = 1;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNameKey() { return nameKey; }
    public void setNameKey(String nameKey) { this.nameKey = nameKey; }

    public String getMarketplaceUrl() { return marketplaceUrl; }
    public void setMarketplaceUrl(String marketplaceUrl) { this.marketplaceUrl = marketplaceUrl; }

    public String getEncryptedApiKey() { return encryptedApiKey; }
    public void setEncryptedApiKey(String encryptedApiKey) { this.encryptedApiKey = encryptedApiKey; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public short getEnabled() { return enabled; }
    public void setEnabled(short enabled) { this.enabled = enabled; }

    public short getActive() { return active; }
    public void setActive(short active) { this.active = active; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
