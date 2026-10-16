package nl.metafactory.agents.gitworkspace;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single-use, short-lived tickets that let one workflow start be bound to a git workspace. ai-control's
 * service identity issues a ticket for a workspace, a workflow and the subject ({@code sub}) of the
 * user who will start it; the start redeems it with that user's own token. A ticket is consumed by its
 * first redemption attempt, whether it matches or not, so it cannot be guessed or replayed. Tickets are
 * secrets: never logged. Held in memory (lost on restart: the start then fails closed).
 */
@Component
public class GitWorkspaceTickets {

    /** At most this many unredeemed tickets are kept; issuing beyond it is refused. */
    static final int MAX_OUTSTANDING = 1_000;

    record Ticket(GitWorkspaceBinding workspace, String workflowId, String subject, Instant expiresAt) {
    }

    public record Issued(String ticket, Instant expiresAt) {
        @Override
        public String toString() {
            return "Issued[ticket=***, expiresAt=" + expiresAt + "]";
        }
    }

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final GitWorkspaceProperties properties;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public GitWorkspaceTickets(GitWorkspaceProperties properties) {
        this(properties, Clock.systemUTC());
    }

    GitWorkspaceTickets(GitWorkspaceProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public Issued issue(GitWorkspaceBinding workspace, String workflowId, String subject) {
        Objects.requireNonNull(workspace, "workspace");
        if (workflowId == null || workflowId.isBlank() || subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("a git workspace ticket needs a workflow and a subject");
        }
        Instant now = clock.instant();
        tickets.values().removeIf(t -> !t.expiresAt().isAfter(now));
        if (tickets.size() >= MAX_OUTSTANDING) {
            throw new IllegalStateException("too many outstanding git workspace tickets");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = now.plus(properties.getTicketTtl());
        tickets.put(ticket, new Ticket(workspace, workflowId, subject, expiresAt));
        return new Issued(ticket, expiresAt);
    }

    /**
     * The ticket's workspace when it is known, unexpired, issued for {@code workflowId} and for
     * {@code subject}; empty otherwise. Either way the ticket is used up.
     */
    public Optional<GitWorkspaceBinding> redeem(String ticket, String workflowId, String subject) {
        if (ticket == null || ticket.isBlank()) {
            return Optional.empty();
        }
        Ticket found = tickets.remove(ticket);
        if (found == null || !found.expiresAt().isAfter(clock.instant())
                || !found.workflowId().equals(workflowId) || !found.subject().equals(subject)) {
            return Optional.empty();
        }
        return Optional.of(found.workspace());
    }
}
