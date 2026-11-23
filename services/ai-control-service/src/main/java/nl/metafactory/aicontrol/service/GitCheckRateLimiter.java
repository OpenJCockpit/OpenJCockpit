package nl.metafactory.aicontrol.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/** Sliding one-minute window per user for the stateless Git check, which makes the server contact arbitrary hosts. */
@Component
public class GitCheckRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;

    private final int limitPerMinute;
    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Long>> calls = new ConcurrentHashMap<>();

    @Autowired
    public GitCheckRateLimiter(@Value("${openjcockpit.git.check-rate-limit-per-minute:20}") int limitPerMinute) {
        this(limitPerMinute, Clock.systemUTC());
    }

    GitCheckRateLimiter(int limitPerMinute, Clock clock) {
        this.limitPerMinute = limitPerMinute;
        this.clock = clock;
    }

    /** @throws ResponseStatusException 429 when the user is over the limit */
    public void acquire(String user) {
        long now = clock.millis();
        var window = calls.computeIfAbsent(user, u -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() >= WINDOW_MILLIS) {
                window.pollFirst();
            }
            if (window.size() >= limitPerMinute) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Too many Git checks; try again in a minute");
            }
            window.addLast(now);
        }
    }
}
