package nl.metafactory.aicontrol.service;

import jakarta.annotation.PreDestroy;
import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.client.SkillSpecDto;
import nl.metafactory.aicontrol.config.SkillsMarketplaceProperties;
import nl.metafactory.aicontrol.integration.skillsmarketplace.ExternalSkill;
import nl.metafactory.aicontrol.integration.skillsmarketplace.MarketplaceCatalog;
import nl.metafactory.aicontrol.integration.skillsmarketplace.MarketplaceQuery;
import nl.metafactory.aicontrol.integration.skillsmarketplace.SkillsMarketplaceClient;
import nl.metafactory.aicontrol.model.ExternalSkillDto;
import nl.metafactory.aicontrol.model.SkillCatalogDto;
import nl.metafactory.aicontrol.model.SkillCatalogSourceKind;
import nl.metafactory.aicontrol.model.SkillCatalogSourceOutcome;
import nl.metafactory.aicontrol.model.SkillCatalogSourceStatusDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Aggregates the read-only skill catalog: local skills (from {@code embabel-agent-service}) plus
 * every active+enabled marketplace connection's offerings, each carrying an honest per-source
 * status (architecture §6.4, ADR-5, ADR-6).
 *
 * <p>Concurrency uses a privately-owned {@code ExecutorService} — never a Spring {@code @Bean} —
 * because {@code AsyncConfig} exposes exactly one {@code Executor} bean ({@code workflowExecutor})
 * that Spring currently selects as the default {@code @Async} executor; a second
 * {@code Executor}-assignable bean would silently demote every existing {@code @Async} method
 * (ADR-5). Marketplace tasks are submitted first and run off-thread; the local fetch runs on the
 * request thread so the caller's JWT stays available to {@code embabelWebClient}'s relay filter
 * and marketplace tasks never see a {@code SecurityContext} (reinforcing BR-9).</p>
 */
@Service
public class SkillCatalogService {

    private static final Logger log = LoggerFactory.getLogger(SkillCatalogService.class);

    private final SkillsMarketplaceConnectionService connectionService;
    private final EmbabelAgentClient embabelAgentClient;
    private final SkillsMarketplaceClient marketplaceClient;
    private final SkillsMarketplaceProperties properties;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public SkillCatalogService(SkillsMarketplaceConnectionService connectionService,
                                EmbabelAgentClient embabelAgentClient,
                                SkillsMarketplaceClient marketplaceClient,
                                SkillsMarketplaceProperties properties) {
        this.connectionService = connectionService;
        this.embabelAgentClient = embabelAgentClient;
        this.marketplaceClient = marketplaceClient;
        this.properties = properties;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    public SkillCatalogDto getCatalog() {
        Instant start = Instant.now();
        Instant deadline = start.plusSeconds(properties.getRequestTimeoutSeconds());

        List<MarketplaceQueryTarget> targets = connectionService.listQueryTargets();
        boolean overCapacity = targets.size() > properties.getMaxConnectionsPerFetch();

        List<PendingFetch> pending = new ArrayList<>();
        List<SkillCatalogSourceStatusDto> sources = new ArrayList<>();

        if (!overCapacity) {
            for (MarketplaceQueryTarget target : targets) {
                if (target instanceof MarketplaceQueryTarget.Queryable queryable) {
                    Instant submittedAt = Instant.now();
                    Future<MarketplaceCatalog> future = executor.submit(() -> marketplaceClient.fetchCatalog(
                            new MarketplaceQuery(queryable.id(), queryable.name(), queryable.marketplaceUrl(), queryable.apiKey())));
                    pending.add(new PendingFetch(queryable, future, submittedAt));
                } else if (target instanceof MarketplaceQueryTarget.Unusable unusable) {
                    recordConfigError(unusable, sources);
                }
            }
        }

        // Local fetch runs on the request thread — see class Javadoc for why.
        List<SkillSpecDto> localSkills;
        SkillCatalogSourceOutcome localOutcome;
        Instant localStart = Instant.now();
        try {
            localSkills = embabelAgentClient.listSkillSpecsOrThrow();
            localOutcome = SkillCatalogSourceOutcome.SUCCESS;
        } catch (RuntimeException e) {
            localSkills = List.of();
            localOutcome = SkillCatalogSourceOutcome.UNREACHABLE;
        }
        long localDurationMs = Duration.between(localStart, Instant.now()).toMillis();
        logAttempt(null, "Local", localOutcome, localSkills.size(), localDurationMs);
        sources.add(new SkillCatalogSourceStatusDto(
                SkillCatalogSourceKind.LOCAL, null, "Local", localOutcome, localSkills.size(), null));

        if (overCapacity) {
            for (MarketplaceQueryTarget target : targets) {
                recordBlockedByCapacity(target, sources);
            }
        }

        List<ExternalSkillDto> externalSkills = new ArrayList<>();
        for (PendingFetch fetch : pending) {
            MarketplaceCatalog catalog = resolve(fetch, deadline);
            long durationMs = Duration.between(fetch.submittedAt(), Instant.now()).toMillis();
            logAttempt(fetch.target().id(), fetch.target().name(), catalog.outcome(), catalog.skills().size(), durationMs);
            sources.add(new SkillCatalogSourceStatusDto(SkillCatalogSourceKind.MARKETPLACE, fetch.target().id(),
                    fetch.target().name(), catalog.outcome(), catalog.skills().size(), catalog.httpStatus()));
            for (ExternalSkill skill : catalog.skills()) {
                externalSkills.add(new ExternalSkillDto(fetch.target().id(), fetch.target().name(),
                        skill.name(), skill.description()));
            }
        }

        return new SkillCatalogDto(localSkills, List.copyOf(externalSkills), List.copyOf(sources));
    }

    private MarketplaceCatalog resolve(PendingFetch fetch, Instant deadline) {
        long remainingMillis = Duration.between(Instant.now(), deadline).toMillis();
        try {
            return fetch.future().get(remainingMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            fetch.future().cancel(true);
            return MarketplaceCatalog.failure(SkillCatalogSourceOutcome.TIMEOUT, null);
        } catch (ExecutionException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return MarketplaceCatalog.failure(SkillCatalogSourceOutcome.UNREACHABLE, null);
        }
    }

    private void recordConfigError(MarketplaceQueryTarget.Unusable unusable, List<SkillCatalogSourceStatusDto> sources) {
        logAttempt(unusable.id(), unusable.name(), SkillCatalogSourceOutcome.CONFIG_ERROR, 0, 0L);
        sources.add(new SkillCatalogSourceStatusDto(SkillCatalogSourceKind.MARKETPLACE, unusable.id(), unusable.name(),
                SkillCatalogSourceOutcome.CONFIG_ERROR, 0, null));
    }

    private void recordBlockedByCapacity(MarketplaceQueryTarget target, List<SkillCatalogSourceStatusDto> sources) {
        UUID id = target instanceof MarketplaceQueryTarget.Queryable q ? q.id() : ((MarketplaceQueryTarget.Unusable) target).id();
        String name = target instanceof MarketplaceQueryTarget.Queryable q ? q.name() : ((MarketplaceQueryTarget.Unusable) target).name();
        logAttempt(id, name, SkillCatalogSourceOutcome.BLOCKED_BY_POLICY, 0, 0L);
        sources.add(new SkillCatalogSourceStatusDto(SkillCatalogSourceKind.MARKETPLACE, id, name,
                SkillCatalogSourceOutcome.BLOCKED_BY_POLICY, 0, null));
    }

    // AC-41: one structured line per attempt — connection id, name, outcome, item count, duration.
    // Never a credential, never a verbatim marketplace response body.
    private void logAttempt(UUID connectionId, String connectionName, SkillCatalogSourceOutcome outcome,
                             int itemCount, long durationMs) {
        log.info("Skill catalog fetch attempt connectionId={} connectionName={} outcome={} itemCount={} durationMs={}",
                connectionId, connectionName, outcome, itemCount, durationMs);
    }

    private record PendingFetch(MarketplaceQueryTarget.Queryable target, Future<MarketplaceCatalog> future,
                                 Instant submittedAt) {
    }
}
