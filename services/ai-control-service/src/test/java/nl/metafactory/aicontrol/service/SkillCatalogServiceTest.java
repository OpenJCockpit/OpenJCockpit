package nl.metafactory.aicontrol.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.client.SkillSpecDto;
import nl.metafactory.aicontrol.config.SkillsMarketplaceProperties;
import nl.metafactory.aicontrol.integration.skillsmarketplace.ExternalSkill;
import nl.metafactory.aicontrol.integration.skillsmarketplace.MarketplaceCatalog;
import nl.metafactory.aicontrol.integration.skillsmarketplace.MarketplaceQuery;
import nl.metafactory.aicontrol.integration.skillsmarketplace.SkillsMarketplaceClient;
import nl.metafactory.aicontrol.model.SkillCatalogSourceKind;
import nl.metafactory.aicontrol.model.SkillCatalogSourceOutcome;
import nl.metafactory.aicontrol.model.SkillCatalogSourceStatusDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillCatalogServiceTest {

    @Mock
    private SkillsMarketplaceConnectionService connectionService;

    @Mock
    private EmbabelAgentClient embabelAgentClient;

    @Mock
    private SkillsMarketplaceClient marketplaceClient;

    private SkillsMarketplaceProperties properties;
    private SkillCatalogService service;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        properties = new SkillsMarketplaceProperties();
        service = new SkillCatalogService(connectionService, embabelAgentClient, marketplaceClient, properties);

        logAppender = new ListAppender<>();
        logAppender.start();
        Logger logbackLogger = (Logger) LoggerFactory.getLogger(SkillCatalogService.class);
        logbackLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        Logger logbackLogger = (Logger) LoggerFactory.getLogger(SkillCatalogService.class);
        logbackLogger.detachAppender(logAppender);
    }

    private MarketplaceQueryTarget.Queryable queryable(String name) {
        return new MarketplaceQueryTarget.Queryable(UUID.randomUUID(), name, "https://" + name + ".example.com", "key-" + name);
    }

    // ── AC-04 / AC-05 / AC-06: zero, disabled, or soft-deleted connections ──────
    // (the actual DB filtering is SkillsMarketplaceConnectionService's job and is tested there;
    // here we prove the service behaves correctly when it is handed an empty target list)

    @Test
    void zeroConnectionsProducesOnlyTheLocalSourceAndNoOutboundInteraction() {
        when(connectionService.listQueryTargets()).thenReturn(List.of());
        when(embabelAgentClient.listSkillSpecsOrThrow()).thenReturn(List.of(skill("code-review")));

        var catalog = service.getCatalog();

        assertThat(catalog.localSkills()).extracting(SkillSpecDto::name).containsExactly("code-review");
        assertThat(catalog.externalSkills()).isEmpty();
        assertThat(catalog.sources()).hasSize(1);
        assertThat(catalog.sources().get(0).kind()).isEqualTo(SkillCatalogSourceKind.LOCAL);
        verifyNoInteractions(marketplaceClient);
    }

    // ── AC-07: empty marketplace catalogue ───────────────────────────────────

    @Test
    void emptyMarketplaceCatalogueProducesNoRowsAndNoError() {
        var target = queryable("Acme Skills");
        when(connectionService.listQueryTargets()).thenReturn(List.of(target));
        when(embabelAgentClient.listSkillSpecsOrThrow()).thenReturn(List.of());
        when(marketplaceClient.fetchCatalog(any())).thenReturn(MarketplaceCatalog.success(200, List.of()));

        var catalog = service.getCatalog();

        assertThat(catalog.externalSkills()).isEmpty();
        var marketplaceSource = sourceFor(catalog.sources(), target.id());
        assertThat(marketplaceSource.outcome()).isEqualTo(SkillCatalogSourceOutcome.SUCCESS);
        assertThat(marketplaceSource.itemCount()).isZero();
    }

    // ── AC-08: zero local skills, one marketplace with skills ────────────────

    @Test
    void zeroLocalSkillsWithOneMarketplaceAttributesAllRowsToThatMarketplace() {
        var target = queryable("Acme Skills");
        when(connectionService.listQueryTargets()).thenReturn(List.of(target));
        when(embabelAgentClient.listSkillSpecsOrThrow()).thenReturn(List.of());
        when(marketplaceClient.fetchCatalog(any())).thenReturn(MarketplaceCatalog.success(200,
                List.of(new ExternalSkill("a", "d1"), new ExternalSkill("b", "d2"))));

        var catalog = service.getCatalog();

        assertThat(catalog.localSkills()).isEmpty();
        assertThat(catalog.externalSkills()).hasSize(2);
        assertThat(catalog.externalSkills()).allSatisfy(s -> assertThat(s.marketplaceId()).isEqualTo(target.id()));
    }

    // ── AC-11 / AC-16: partial and total marketplace failure ─────────────────

    @Test
    void onePartialFailureStillRendersLocalAndTheSuccessfulMarketplace() {
        var failing = queryable("Failing");
        var succeeding = queryable("Succeeding");
        when(connectionService.listQueryTargets()).thenReturn(List.of(failing, succeeding));
        when(embabelAgentClient.listSkillSpecsOrThrow()).thenReturn(List.of(skill("code-review")));
        when(marketplaceClient.fetchCatalog(argThatMatches(failing)))
                .thenReturn(MarketplaceCatalog.failure(SkillCatalogSourceOutcome.TIMEOUT, null));
        when(marketplaceClient.fetchCatalog(argThatMatches(succeeding)))
                .thenReturn(MarketplaceCatalog.success(200, List.of(new ExternalSkill("x", "y"))));

        var catalog = service.getCatalog();

        assertThat(catalog.localSkills()).extracting(SkillSpecDto::name).containsExactly("code-review");
        assertThat(sourceFor(catalog.sources(), failing.id()).outcome()).isEqualTo(SkillCatalogSourceOutcome.TIMEOUT);
        assertThat(sourceFor(catalog.sources(), succeeding.id()).outcome()).isEqualTo(SkillCatalogSourceOutcome.SUCCESS);
        assertThat(catalog.externalSkills()).hasSize(1);
    }

    @Test
    void allMarketplacesFailingStillListsLocalSkillsAndReportsEachIndividually() {
        var m1 = queryable("Marketplace1");
        var m2 = queryable("Marketplace2");
        when(connectionService.listQueryTargets()).thenReturn(List.of(m1, m2));
        when(embabelAgentClient.listSkillSpecsOrThrow()).thenReturn(List.of(skill("code-review")));
        when(marketplaceClient.fetchCatalog(argThatMatches(m1)))
                .thenReturn(MarketplaceCatalog.failure(SkillCatalogSourceOutcome.AUTH_FAILED, 401));
        when(marketplaceClient.fetchCatalog(argThatMatches(m2)))
                .thenReturn(MarketplaceCatalog.failure(SkillCatalogSourceOutcome.UNREACHABLE, null));

        var catalog = service.getCatalog();

        assertThat(catalog.localSkills()).extracting(SkillSpecDto::name).containsExactly("code-review");
        assertThat(sourceFor(catalog.sources(), m1.id()).outcome()).isEqualTo(SkillCatalogSourceOutcome.AUTH_FAILED);
        assertThat(sourceFor(catalog.sources(), m2.id()).outcome()).isEqualTo(SkillCatalogSourceOutcome.UNREACHABLE);
        assertThat(catalog.externalSkills()).isEmpty();
    }

    // ── Local-source honesty (ADR-6, BR-7) ───────────────────────────────────

    @Test
    void localFetchFailureIsReportedAsAnHonestSourceOutcomeAndDoesNotThrow() {
        when(connectionService.listQueryTargets()).thenReturn(List.of());
        when(embabelAgentClient.listSkillSpecsOrThrow()).thenThrow(new RuntimeException("embabel-agent-service is down"));

        var catalog = service.getCatalog();

        assertThat(catalog.localSkills()).isEmpty();
        assertThat(catalog.sources()).hasSize(1);
        assertThat(catalog.sources().get(0).outcome()).isNotEqualTo(SkillCatalogSourceOutcome.SUCCESS);
    }

    // ── AC-17/18: bounded worst case, no fixed sleeps, latch-based hang ──────

    @Test
    void boundedWorstCaseWhenMultipleMarketplacesHangConcurrently() {
        var latch = new CountDownLatch(1); // deliberately never counted down
        SkillsMarketplaceClient hangingClient = query -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return MarketplaceCatalog.success(200, List.of());
        };
        properties.setRequestTimeoutSeconds(1);
        var hangingService = new SkillCatalogService(connectionService, embabelAgentClient, hangingClient, properties);

        var m1 = queryable("Slow1");
        var m2 = queryable("Slow2");
        when(connectionService.listQueryTargets()).thenReturn(List.of(m1, m2));
        when(embabelAgentClient.listSkillSpecsOrThrow()).thenReturn(List.of());

        long startNanos = System.nanoTime();
        var catalog = hangingService.getCatalog();
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        // Two concurrently-hanging marketplaces must not sum their timeouts (bounded worst case).
        assertThat(elapsedMs).isLessThan(2_500);
        assertThat(sourceFor(catalog.sources(), m1.id()).outcome()).isEqualTo(SkillCatalogSourceOutcome.TIMEOUT);
        assertThat(sourceFor(catalog.sources(), m2.id()).outcome()).isEqualTo(SkillCatalogSourceOutcome.TIMEOUT);
    }

    // ── AC-41: one structured log line per attempt, no credential ───────────

    @Test
    void logsOneStructuredLineWithNoCredentialForEveryAttempt() {
        var target = queryable("Acme Skills");
        when(connectionService.listQueryTargets()).thenReturn(List.of(target));
        when(embabelAgentClient.listSkillSpecsOrThrow()).thenReturn(List.of());
        when(marketplaceClient.fetchCatalog(any())).thenReturn(MarketplaceCatalog.success(200, List.of()));

        service.getCatalog();

        assertThat(logAppender.list).hasSizeGreaterThanOrEqualTo(2); // local + one marketplace
        for (ILoggingEvent event : logAppender.list) {
            assertThat(event.getFormattedMessage())
                    .doesNotContain("key-Acme Skills")
                    .contains("outcome=");
        }
    }

    // ── AC-47: Unusable connections never touch the marketplace client ──────

    @Test
    void unusableConnectionIsReportedAsConfigErrorWithoutAnyOutboundInteraction() {
        var unusable = new MarketplaceQueryTarget.Unusable(UUID.randomUUID(), "Acme Skills", "missing api key");
        when(connectionService.listQueryTargets()).thenReturn(List.of(unusable));
        when(embabelAgentClient.listSkillSpecsOrThrow()).thenReturn(List.of());

        var catalog = service.getCatalog();

        assertThat(sourceFor(catalog.sources(), unusable.id()).outcome()).isEqualTo(SkillCatalogSourceOutcome.CONFIG_ERROR);
        verifyNoInteractions(marketplaceClient);
    }

    // ── Over-capacity circuit breaker (O-2) ──────────────────────────────────

    @Test
    void connectionCountBeyondCapIsBlockedByPolicyForEveryConnectionWithoutAnyOutboundInteraction() {
        properties.setMaxConnectionsPerFetch(1);
        var m1 = queryable("One");
        var m2 = queryable("Two");
        when(connectionService.listQueryTargets()).thenReturn(List.of(m1, m2));
        when(embabelAgentClient.listSkillSpecsOrThrow()).thenReturn(List.of());

        var catalog = service.getCatalog();

        assertThat(sourceFor(catalog.sources(), m1.id()).outcome()).isEqualTo(SkillCatalogSourceOutcome.BLOCKED_BY_POLICY);
        assertThat(sourceFor(catalog.sources(), m2.id()).outcome()).isEqualTo(SkillCatalogSourceOutcome.BLOCKED_BY_POLICY);
        verifyNoInteractions(marketplaceClient);
    }

    // ── AC-48: substituting the seam implementation needs no change here ────

    @Test
    void alternativeSeamImplementationRequiresNoChangeToMergeOrStatusLogic() {
        var target = queryable("Acme Skills");
        when(connectionService.listQueryTargets()).thenReturn(List.of(target));
        when(embabelAgentClient.listSkillSpecsOrThrow()).thenReturn(List.of());

        SkillsMarketplaceClient stubA = query -> MarketplaceCatalog.success(200, List.of(new ExternalSkill("from-a", null)));
        SkillsMarketplaceClient stubB = query -> MarketplaceCatalog.success(200, List.of(new ExternalSkill("from-b", null)));

        var serviceWithA = new SkillCatalogService(connectionService, embabelAgentClient, stubA, properties);
        var serviceWithB = new SkillCatalogService(connectionService, embabelAgentClient, stubB, properties);

        assertThat(serviceWithA.getCatalog().externalSkills()).extracting(dto -> dto.name()).containsExactly("from-a");
        assertThat(serviceWithB.getCatalog().externalSkills()).extracting(dto -> dto.name()).containsExactly("from-b");
    }

    // ── unexpected task failure (ExecutionException) ─────────────────────────

    @Test
    void unexpectedExceptionFromTheSeamIsClassifiedAsUnreachableRatherThanPropagating() {
        var target = queryable("Acme Skills");
        when(connectionService.listQueryTargets()).thenReturn(List.of(target));
        when(embabelAgentClient.listSkillSpecsOrThrow()).thenReturn(List.of());
        SkillsMarketplaceClient brokenClient = query -> {
            throw new IllegalStateException("unexpected bug in the seam implementation");
        };
        var brokenService = new SkillCatalogService(connectionService, embabelAgentClient, brokenClient, properties);

        var catalog = brokenService.getCatalog();

        assertThat(sourceFor(catalog.sources(), target.id()).outcome()).isEqualTo(SkillCatalogSourceOutcome.UNREACHABLE);
    }

    // ── @PreDestroy lifecycle ─────────────────────────────────────────────────

    @Test
    void shutdownStopsThePrivatelyOwnedExecutorWithoutThrowing() {
        service.shutdown();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private SkillSpecDto skill(String name) {
        return new SkillSpecDto(name, "desc", "in", "out", "instructions", List.of(), null);
    }

    private SkillCatalogSourceStatusDto sourceFor(List<SkillCatalogSourceStatusDto> sources, UUID marketplaceId) {
        return sources.stream()
                .filter(s -> marketplaceId.equals(s.marketplaceId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no source for marketplaceId " + marketplaceId));
    }

    private MarketplaceQuery argThatMatches(MarketplaceQueryTarget.Queryable target) {
        return argThat(q -> q != null && q.connectionId().equals(target.id()));
    }
}
