package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.model.SkillsMarketplaceConnection;
import nl.metafactory.aicontrol.model.SkillsMarketplaceConnectionDto;
import nl.metafactory.aicontrol.model.SkillsMarketplaceConnectionRequest;
import nl.metafactory.aicontrol.repository.SkillsMarketplaceConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Domain logic for global skills marketplace connections (ADR-1: no dependency on
 * {@code ProjectService}/{@code ProjectRepository} — this resource is not project-scoped).
 *
 * <p>Business invariants owned here: case-insensitive name uniqueness among non-removed
 * connections (BR-1, ADR-2), API-key-required-on-create (BR-5), secret retention on update
 * (BR-2), and soft-delete with secret clearing (BR-3).</p>
 *
 * <p>Security: no log statement or exception message in this class may ever contain the raw
 * or encrypted API key — only {@code id}, {@code name}, and {@code actor} are logged.</p>
 */
@Service
@Transactional
public class SkillsMarketplaceConnectionService {

    private static final Logger log = LoggerFactory.getLogger(SkillsMarketplaceConnectionService.class);

    private final SkillsMarketplaceConnectionRepository repository;
    private final CredentialEncryptionService encryptionService;

    public SkillsMarketplaceConnectionService(SkillsMarketplaceConnectionRepository repository,
                                               CredentialEncryptionService encryptionService) {
        this.repository = repository;
        this.encryptionService = encryptionService;
    }

    @Transactional(readOnly = true)
    public List<SkillsMarketplaceConnectionDto> list() {
        return repository.findAllByActiveOrderByNameIgnoreCase((short) 1).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Resolves every active+enabled connection (BR-1) into a {@link MarketplaceQueryTarget},
     * decrypting the stored key along the way. Decryption stays inside this class because it
     * already owns {@link CredentialEncryptionService} — see architecture §6.5.
     */
    @Transactional(readOnly = true)
    public List<MarketplaceQueryTarget> listQueryTargets() {
        return repository.findAllByActiveAndEnabledOrderByNameIgnoreCase((short) 1, (short) 1).stream()
                .map(this::toQueryTarget)
                .toList();
    }

    private MarketplaceQueryTarget toQueryTarget(SkillsMarketplaceConnection c) {
        if (isBlank(c.getEncryptedApiKey())) {
            return new MarketplaceQueryTarget.Unusable(c.getId(), c.getName(), "missing api key");
        }
        try {
            String apiKey = encryptionService.decrypt(c.getEncryptedApiKey());
            if (isBlank(apiKey)) {
                return new MarketplaceQueryTarget.Unusable(c.getId(), c.getName(), "missing api key");
            }
            return new MarketplaceQueryTarget.Queryable(c.getId(), c.getName(), c.getMarketplaceUrl(), apiKey);
        } catch (RuntimeException e) {
            // Never let a decryption failure surface the ciphertext or the exception message —
            // both could echo secret material.
            return new MarketplaceQueryTarget.Unusable(c.getId(), c.getName(), "undecryptable api key");
        }
    }

    public SkillsMarketplaceConnectionDto create(SkillsMarketplaceConnectionRequest req, String actor) {
        String name = req.name().trim();
        String nameKey = normalize(name);

        if (isBlank(req.apiKey())) {
            throw new SkillsMarketplaceException(SkillsMarketplaceException.Code.API_KEY_REQUIRED,
                    "apiKey is required when creating a skills marketplace connection");
        }
        if (repository.findByNameKey(nameKey).isPresent()) {
            throw new SkillsMarketplaceException(SkillsMarketplaceException.Code.NAME_CONFLICT,
                    "A skills marketplace connection named '" + name + "' already exists");
        }

        var entity = new SkillsMarketplaceConnection();
        entity.setName(name);
        entity.setNameKey(nameKey);
        entity.setMarketplaceUrl(req.marketplaceUrl());
        entity.setDescription(req.description());
        entity.setEnabled((short) (req.enabled() ? 1 : 0));
        entity.setActive((short) 1);
        entity.setCreatedBy(actor);
        applyApiKey(req.apiKey(), entity::setEncryptedApiKey);

        SkillsMarketplaceConnection saved = saveOrConflict(entity, name);
        log.info("Skills marketplace connection created id={} name={} by={}", saved.getId(), saved.getName(), actor);
        return toDto(saved);
    }

    public SkillsMarketplaceConnectionDto update(UUID id, SkillsMarketplaceConnectionRequest req, String actor) {
        var entity = repository.findByIdAndActive(id, (short) 1)
                .orElseThrow(() -> new SkillsMarketplaceException(SkillsMarketplaceException.Code.NOT_FOUND,
                        "No skills marketplace connection with id " + id));

        String name = req.name().trim();
        String nameKey = normalize(name);
        if (!nameKey.equals(entity.getNameKey())) {
            repository.findByNameKey(nameKey)
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw new SkillsMarketplaceException(SkillsMarketplaceException.Code.NAME_CONFLICT,
                                "A skills marketplace connection named '" + name + "' already exists");
                    });
        }

        entity.setName(name);
        entity.setNameKey(nameKey);
        entity.setMarketplaceUrl(req.marketplaceUrl());
        entity.setDescription(req.description());
        entity.setEnabled((short) (req.enabled() ? 1 : 0));
        applyApiKey(req.apiKey(), entity::setEncryptedApiKey);

        // The entity's @PreUpdate callback only fires at flush time, i.e. on transaction commit —
        // which happens AFTER this method returns its DTO built from the in-memory entity. Setting
        // updatedAt explicitly here ensures the immediately-returned DTO already carries the fresh
        // timestamp; @PreUpdate will (redundantly but harmlessly) set an equal-or-later value again
        // at flush.
        entity.setUpdatedAt(Instant.now());

        SkillsMarketplaceConnection saved = saveOrConflict(entity, name);
        log.info("Skills marketplace connection updated id={} name={} by={}", saved.getId(), saved.getName(), actor);
        return toDto(saved);
    }

    public void delete(UUID id, String actor) {
        var entity = repository.findByIdAndActive(id, (short) 1)
                .orElseThrow(() -> new SkillsMarketplaceException(SkillsMarketplaceException.Code.NOT_FOUND,
                        "No skills marketplace connection with id " + id));

        entity.setActive((short) 0);
        entity.setEncryptedApiKey(null);
        entity.setNameKey(null);
        repository.save(entity);
        log.info("Skills marketplace connection deleted id={} name={} by={}", entity.getId(), entity.getName(), actor);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private SkillsMarketplaceConnection saveOrConflict(SkillsMarketplaceConnection entity, String name) {
        try {
            return repository.save(entity);
        } catch (DataIntegrityViolationException e) {
            // Race backstop: two concurrent creates/renames can both pass the findByNameKey
            // pre-check before either commits. The unique index on name_key is the last line
            // of defence (ADR-2); never surface e.getMessage() here, it may echo column values.
            throw new SkillsMarketplaceException(SkillsMarketplaceException.Code.NAME_CONFLICT,
                    "A skills marketplace connection named '" + name + "' already exists");
        }
    }

    // Deliberate, documented deviation from the literal ProjectIntegrationConfigController
    // .applyAuthToken three-branch shape: that helper treats an *empty string* as an explicit
    // "clear the credential" instruction, distinct from "not provided" (null). This resource has
    // no such explicit-clear affordance (BR-3's soft-delete is the only way to clear a key); BR-2
    // and AC-05 require that ANY blank apiKey leaves encryptedApiKey byte-for-byte unchanged, i.e.
    // the setter must never be invoked. On create, a blank apiKey is already rejected upstream
    // (API_KEY_REQUIRED) before this method is ever called, so "blank" in practice only occurs on
    // update, where "unchanged" is exactly the required behaviour — there is no third,
    // hadExisting-dependent branch to preserve.
    private void applyApiKey(String rawKey, Consumer<String> setter) {
        if (!isBlank(rawKey)) {
            setter.accept(encryptionService.encrypt(rawKey));
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private SkillsMarketplaceConnectionDto toDto(SkillsMarketplaceConnection c) {
        return new SkillsMarketplaceConnectionDto(
                c.getId(),
                c.getName(),
                c.getMarketplaceUrl(),
                c.getDescription(),
                c.getEnabled() == 1,
                c.getEncryptedApiKey() != null,
                c.getCreatedBy(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
