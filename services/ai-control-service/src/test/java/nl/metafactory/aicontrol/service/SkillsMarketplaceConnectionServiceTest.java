package nl.metafactory.aicontrol.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import nl.metafactory.aicontrol.model.SkillsMarketplaceConnection;
import nl.metafactory.aicontrol.model.SkillsMarketplaceConnectionRequest;
import nl.metafactory.aicontrol.repository.SkillsMarketplaceConnectionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillsMarketplaceConnectionServiceTest {

    @Mock
    private SkillsMarketplaceConnectionRepository repository;

    @Mock
    private CredentialEncryptionService encryptionService;

    private SkillsMarketplaceConnectionService service;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        service = new SkillsMarketplaceConnectionService(repository, encryptionService);

        logAppender = new ListAppender<>();
        logAppender.start();
        Logger logbackLogger = (Logger) LoggerFactory.getLogger(SkillsMarketplaceConnectionService.class);
        logbackLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        Logger logbackLogger = (Logger) LoggerFactory.getLogger(SkillsMarketplaceConnectionService.class);
        logbackLogger.detachAppender(logAppender);
    }

    // ── list ─────────────────────────────────────────────────────────────────

    @Test
    void listMapsEntitiesToDtosOrderedByRepository() {
        var c1 = existing(UUID.randomUUID(), "Acme", "acme", null);
        var c2 = existing(UUID.randomUUID(), "Beta", "beta", "cipher");
        when(repository.findAllByActiveOrderByNameIgnoreCase((short) 1)).thenReturn(List.of(c1, c2));

        List<nl.metafactory.aicontrol.model.SkillsMarketplaceConnectionDto> result = service.list();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Acme");
        assertThat(result.get(0).hasApiKey()).isFalse();
        assertThat(result.get(1).name()).isEqualTo("Beta");
        assertThat(result.get(1).hasApiKey()).isTrue();
    }

    // ── create ───────────────────────────────────────────────────────────────

    @Test
    void createEncryptsKeyNormalisesNameAndSetsCreatedBy() {
        when(repository.findByNameKey("acme skills")).thenReturn(Optional.empty());
        when(encryptionService.encrypt("secret-key-value")).thenReturn("cipher-text");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new SkillsMarketplaceConnectionRequest(" Acme Skills ", "https://marketplace.example.com/api",
                "secret-key-value", "desc", true);

        var dto = service.create(req, "ricky");

        assertThat(dto.name()).isEqualTo("Acme Skills");
        assertThat(dto.hasApiKey()).isTrue();
        assertThat(dto.createdBy()).isEqualTo("ricky");
        assertThat(dto.enabled()).isTrue();
        verify(encryptionService, times(1)).encrypt("secret-key-value");

        assertThat(logAppender.list).isNotEmpty();
        for (ILoggingEvent event : logAppender.list) {
            assertThat(event.getFormattedMessage()).doesNotContain("secret-key-value").doesNotContain("cipher-text");
        }
    }

    @Test
    void createRejectsNullApiKey() {
        var req = new SkillsMarketplaceConnectionRequest("Acme Skills", "https://marketplace.example.com/api",
                null, null, true);

        assertThatThrownBy(() -> service.create(req, "ricky"))
                .isInstanceOf(SkillsMarketplaceException.class)
                .extracting(e -> ((SkillsMarketplaceException) e).getCode())
                .isEqualTo(SkillsMarketplaceException.Code.API_KEY_REQUIRED);
    }

    @Test
    void createRejectsBlankApiKey() {
        var req = new SkillsMarketplaceConnectionRequest("Acme Skills", "https://marketplace.example.com/api",
                "   ", null, true);

        assertThatThrownBy(() -> service.create(req, "ricky"))
                .isInstanceOf(SkillsMarketplaceException.class)
                .extracting(e -> ((SkillsMarketplaceException) e).getCode())
                .isEqualTo(SkillsMarketplaceException.Code.API_KEY_REQUIRED);
    }

    @Test
    void createRejectsDuplicateNameCaseInsensitively() {
        when(repository.findByNameKey("acme skills")).thenReturn(Optional.of(existing(UUID.randomUUID(), "Acme Skills", "acme skills", "cipher")));

        var req = new SkillsMarketplaceConnectionRequest("ACME SKILLS", "https://marketplace.example.com/api",
                "secret-key-value", null, true);

        assertThatThrownBy(() -> service.create(req, "ricky"))
                .isInstanceOf(SkillsMarketplaceException.class)
                .extracting(e -> ((SkillsMarketplaceException) e).getCode())
                .isEqualTo(SkillsMarketplaceException.Code.NAME_CONFLICT);
    }

    @Test
    void createRemapsDataIntegrityViolationToNameConflict() {
        when(repository.findByNameKey("acme skills")).thenReturn(Optional.empty());
        when(encryptionService.encrypt(anyString())).thenReturn("cipher");
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        var req = new SkillsMarketplaceConnectionRequest("Acme Skills", "https://marketplace.example.com/api",
                "secret-key-value", null, true);

        assertThatThrownBy(() -> service.create(req, "ricky"))
                .isInstanceOf(SkillsMarketplaceException.class)
                .extracting(e -> ((SkillsMarketplaceException) e).getCode())
                .isEqualTo(SkillsMarketplaceException.Code.NAME_CONFLICT);
    }

    // ── update ───────────────────────────────────────────────────────────────

    @Test
    void updateThrowsNotFoundForUnknownOrSoftDeletedId() {
        var id = UUID.randomUUID();
        when(repository.findByIdAndActive(id, (short) 1)).thenReturn(Optional.empty());

        var req = new SkillsMarketplaceConnectionRequest("Acme Skills", "https://marketplace.example.com/api",
                null, null, true);

        assertThatThrownBy(() -> service.update(id, req, "ricky"))
                .isInstanceOf(SkillsMarketplaceException.class)
                .extracting(e -> ((SkillsMarketplaceException) e).getCode())
                .isEqualTo(SkillsMarketplaceException.Code.NOT_FOUND);
    }

    @Test
    void updateWithNullApiKeyLeavesEncryptedKeyUntouched() {
        var id = UUID.randomUUID();
        var entity = spy(existing(id, "Acme Skills", "acme skills", "old-cipher"));
        when(repository.findByIdAndActive(id, (short) 1)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new SkillsMarketplaceConnectionRequest("Acme Skills", "https://marketplace.example.com/api-v2",
                null, null, true);

        var dto = service.update(id, req, "ricky");

        assertThat(dto.marketplaceUrl()).isEqualTo("https://marketplace.example.com/api-v2");
        assertThat(entity.getEncryptedApiKey()).isEqualTo("old-cipher");
        verify(entity, never()).setEncryptedApiKey(anyString());
        verify(encryptionService, never()).encrypt(anyString());
    }

    @Test
    void updateWithBlankApiKeyLeavesEncryptedKeyUntouched() {
        var id = UUID.randomUUID();
        var entity = spy(existing(id, "Acme Skills", "acme skills", "old-cipher"));
        when(repository.findByIdAndActive(id, (short) 1)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new SkillsMarketplaceConnectionRequest("Acme Skills", "https://marketplace.example.com/api",
                "   ", null, true);

        service.update(id, req, "ricky");

        assertThat(entity.getEncryptedApiKey()).isEqualTo("old-cipher");
        verify(entity, never()).setEncryptedApiKey(anyString());
    }

    @Test
    void updateWithNonBlankApiKeyReEncrypts() {
        var id = UUID.randomUUID();
        var entity = existing(id, "Acme Skills", "acme skills", "old-cipher");
        when(repository.findByIdAndActive(id, (short) 1)).thenReturn(Optional.of(entity));
        when(encryptionService.encrypt("new-secret")).thenReturn("new-cipher");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new SkillsMarketplaceConnectionRequest("Acme Skills", "https://marketplace.example.com/api",
                "new-secret", null, true);

        var dto = service.update(id, req, "ricky");

        assertThat(entity.getEncryptedApiKey()).isEqualTo("new-cipher");
        assertThat(dto.hasApiKey()).isTrue();
    }

    @Test
    void updateRenameToExistingNameConflicts() {
        var id = UUID.randomUUID();
        var otherId = UUID.randomUUID();
        var entity = existing(id, "Acme Skills", "acme skills", "cipher");
        when(repository.findByIdAndActive(id, (short) 1)).thenReturn(Optional.of(entity));
        when(repository.findByNameKey("beta")).thenReturn(Optional.of(existing(otherId, "Beta", "beta", "cipher2")));

        var req = new SkillsMarketplaceConnectionRequest("Beta", "https://marketplace.example.com/api",
                null, null, true);

        assertThatThrownBy(() -> service.update(id, req, "ricky"))
                .isInstanceOf(SkillsMarketplaceException.class)
                .extracting(e -> ((SkillsMarketplaceException) e).getCode())
                .isEqualTo(SkillsMarketplaceException.Code.NAME_CONFLICT);
    }

    @Test
    void updateRenameToFreeNameUpdatesNameKey() {
        var id = UUID.randomUUID();
        var entity = existing(id, "Acme Skills", "acme skills", "cipher");
        when(repository.findByIdAndActive(id, (short) 1)).thenReturn(Optional.of(entity));
        when(repository.findByNameKey("acme skills renamed")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new SkillsMarketplaceConnectionRequest("Acme Skills Renamed", "https://marketplace.example.com/api",
                null, null, true);

        var dto = service.update(id, req, "ricky");

        assertThat(dto.name()).isEqualTo("Acme Skills Renamed");
        assertThat(entity.getNameKey()).isEqualTo("acme skills renamed");
    }

    @Test
    void updateSameNameDoesNotSelfConflict() {
        var id = UUID.randomUUID();
        var entity = existing(id, "Acme Skills", "acme skills", "cipher");
        when(repository.findByIdAndActive(id, (short) 1)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new SkillsMarketplaceConnectionRequest("Acme Skills", "https://marketplace.example.com/api-changed",
                null, null, false);

        var dto = service.update(id, req, "ricky");

        assertThat(dto.marketplaceUrl()).isEqualTo("https://marketplace.example.com/api-changed");
        assertThat(dto.enabled()).isFalse();
        verify(repository, never()).findByNameKey(anyString());
    }

    // Note: this test only proves setUpdatedAt() is invoked with a fresh value before save() is
    // called on the mocked repository. Because Mockito never runs real JPA lifecycle callbacks, it
    // would NOT, on its own, have caught the original bug (the DTO's updatedAt was stale because
    // it relied solely on @PreUpdate firing at flush, i.e. after this method already returns). The
    // real-JPA-backed proof is SkillsMarketplaceConnectionServiceJpaTest.
    @Test
    void updateSetsFreshUpdatedAtOnEntityBeforeSave() {
        var id = UUID.randomUUID();
        // Fixed, clearly-in-the-past value (rather than "now") so the "after" assertion below can
        // never be flaky due to clock resolution. Set on the plain object BEFORE wrapping it in a
        // spy, so this setup call itself is not recorded as an invocation on the spy.
        var originalUpdatedAt = Instant.now().minusSeconds(60);
        var plainEntity = existing(id, "Acme Skills", "acme skills", "cipher");
        plainEntity.setUpdatedAt(originalUpdatedAt);
        var entity = spy(plainEntity);
        when(repository.findByIdAndActive(id, (short) 1)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new SkillsMarketplaceConnectionRequest("Acme Skills", "https://marketplace.example.com/api",
                null, null, true);

        var dto = service.update(id, req, "ricky");

        var captor = org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(entity).setUpdatedAt(captor.capture());
        assertThat(captor.getValue()).isAfter(originalUpdatedAt);
        assertThat(dto.updatedAt()).isEqualTo(captor.getValue());
    }

    @Test
    void updateRemapsDataIntegrityViolationToNameConflict() {
        var id = UUID.randomUUID();
        var entity = existing(id, "Acme Skills", "acme skills", "cipher");
        when(repository.findByIdAndActive(id, (short) 1)).thenReturn(Optional.of(entity));
        when(repository.findByNameKey("renamed")).thenReturn(Optional.empty());
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        var req = new SkillsMarketplaceConnectionRequest("Renamed", "https://marketplace.example.com/api",
                null, null, true);

        assertThatThrownBy(() -> service.update(id, req, "ricky"))
                .isInstanceOf(SkillsMarketplaceException.class)
                .extracting(e -> ((SkillsMarketplaceException) e).getCode())
                .isEqualTo(SkillsMarketplaceException.Code.NAME_CONFLICT);
    }

    // ── delete ───────────────────────────────────────────────────────────────

    @Test
    void deleteSoftDeletesAndClearsSecretAndNameKey() {
        var id = UUID.randomUUID();
        var entity = existing(id, "Acme Skills", "acme skills", "cipher");
        when(repository.findByIdAndActive(id, (short) 1)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.delete(id, "ricky");

        assertThat(entity.getActive()).isEqualTo((short) 0);
        assertThat(entity.getEncryptedApiKey()).isNull();
        assertThat(entity.getNameKey()).isNull();
        verify(repository).save(eq(entity));

        for (ILoggingEvent event : logAppender.list) {
            assertThat(event.getFormattedMessage()).doesNotContain("cipher");
        }
    }

    @Test
    void deleteThrowsNotFoundForUnknownId() {
        var id = UUID.randomUUID();
        when(repository.findByIdAndActive(id, (short) 1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id, "ricky"))
                .isInstanceOf(SkillsMarketplaceException.class)
                .extracting(e -> ((SkillsMarketplaceException) e).getCode())
                .isEqualTo(SkillsMarketplaceException.Code.NOT_FOUND);
    }

    // ── listQueryTargets (skills-tab-marketplace-import, B4) ────────────────────

    @Test
    void listQueryTargetsFiltersByActiveAndEnabledAndDecryptsTheKey() {
        var id = UUID.randomUUID();
        var c = existing(id, "Acme Skills", "acme skills", "cipher-text");
        when(repository.findAllByActiveAndEnabledOrderByNameIgnoreCase((short) 1, (short) 1)).thenReturn(List.of(c));
        when(encryptionService.decrypt("cipher-text")).thenReturn("plaintext-api-key");

        var targets = service.listQueryTargets();

        assertThat(targets).hasSize(1);
        var queryable = (MarketplaceQueryTarget.Queryable) targets.get(0);
        assertThat(queryable.id()).isEqualTo(id);
        assertThat(queryable.name()).isEqualTo("Acme Skills");
        assertThat(queryable.marketplaceUrl()).isEqualTo("https://marketplace.example.com/api");
        assertThat(queryable.apiKey()).isEqualTo("plaintext-api-key");
    }

    @Test
    void listQueryTargetsReportsUnusableForBlankEncryptedKey() {
        var id = UUID.randomUUID();
        var c = existing(id, "Acme Skills", "acme skills", null);
        when(repository.findAllByActiveAndEnabledOrderByNameIgnoreCase((short) 1, (short) 1)).thenReturn(List.of(c));

        var targets = service.listQueryTargets();

        assertThat(targets).hasSize(1);
        var unusable = (MarketplaceQueryTarget.Unusable) targets.get(0);
        assertThat(unusable.id()).isEqualTo(id);
        assertThat(unusable.name()).isEqualTo("Acme Skills");
        assertThat(unusable.reason()).isEqualTo("missing api key");
    }

    @Test
    void listQueryTargetsReportsUnusableWhenDecryptedKeyIsBlank() {
        var id = UUID.randomUUID();
        var c = existing(id, "Acme Skills", "acme skills", "cipher-text");
        when(repository.findAllByActiveAndEnabledOrderByNameIgnoreCase((short) 1, (short) 1)).thenReturn(List.of(c));
        when(encryptionService.decrypt("cipher-text")).thenReturn("   ");

        var targets = service.listQueryTargets();

        var unusable = (MarketplaceQueryTarget.Unusable) targets.get(0);
        assertThat(unusable.reason()).isEqualTo("missing api key");
    }

    @Test
    void listQueryTargetsReportsUnusableWhenDecryptionThrowsAndNeverLeaksTheCiphertextOrExceptionMessage() {
        var id = UUID.randomUUID();
        var c = existing(id, "Acme Skills", "acme skills", "corrupt-cipher-text");
        when(repository.findAllByActiveAndEnabledOrderByNameIgnoreCase((short) 1, (short) 1)).thenReturn(List.of(c));
        when(encryptionService.decrypt("corrupt-cipher-text"))
                .thenThrow(new IllegalStateException("Unable to decrypt corrupt-cipher-text"));

        var targets = service.listQueryTargets();

        var unusable = (MarketplaceQueryTarget.Unusable) targets.get(0);
        assertThat(unusable.reason()).isEqualTo("undecryptable api key");
        for (ILoggingEvent event : logAppender.list) {
            assertThat(event.getFormattedMessage()).doesNotContain("corrupt-cipher-text");
        }
    }

    @Test
    void listQueryTargetsReturnsEmptyWhenNoActiveEnabledConnectionsExist() {
        when(repository.findAllByActiveAndEnabledOrderByNameIgnoreCase((short) 1, (short) 1)).thenReturn(List.of());

        assertThat(service.listQueryTargets()).isEmpty();
    }

    // ── toString() redaction of both key-bearing records (BR-8/AC-26) ───────────

    @Test
    void queryableToStringNeverContainsTheApiKey() {
        var id = UUID.randomUUID();
        var queryable = new MarketplaceQueryTarget.Queryable(id, "Acme Skills", "https://x.example.com", "plaintext-secret");

        assertThat(queryable.toString()).doesNotContain("plaintext-secret").contains("Acme Skills");
    }

    private SkillsMarketplaceConnection existing(UUID id, String name, String nameKey, String encryptedApiKey) {
        var c = new SkillsMarketplaceConnection();
        c.setId(id);
        c.setName(name);
        c.setNameKey(nameKey);
        c.setMarketplaceUrl("https://marketplace.example.com/api");
        c.setEncryptedApiKey(encryptedApiKey);
        c.setEnabled((short) 1);
        c.setActive((short) 1);
        c.setCreatedBy("ricky");
        c.setCreatedAt(Instant.now());
        c.setUpdatedAt(Instant.now());
        return c;
    }
}
