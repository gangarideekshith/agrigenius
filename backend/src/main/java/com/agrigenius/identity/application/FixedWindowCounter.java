package com.agrigenius.identity.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe, memory-bounded fixed-window counter keyed by an opaque string.
 *
 * <p>A key's window starts at its first hit and lasts {@code window}; afterwards the key starts
 * a fresh window. Keys are personal data (mobile numbers, IP addresses) and are never logged.
 */
public final class FixedWindowCounter {

    private static final Logger log = LoggerFactory.getLogger(FixedWindowCounter.class);
    private static final Duration MIN_RETRY_AFTER = Duration.ofSeconds(1);

    private record Window(Instant start, int count) {
    }

    private final int limit;
    private final Duration window;
    private final Clock clock;
    private final int maxKeys;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public FixedWindowCounter(int limit, Duration window, Clock clock, int maxKeys) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }
        if (window == null || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be positive");
        }
        if (maxKeys < 1) {
            throw new IllegalArgumentException("maxKeys must be at least 1");
        }
        this.limit = limit;
        this.window = window;
        this.clock = Objects.requireNonNull(clock);
        this.maxKeys = maxKeys;
    }

    /**
     * Counts one hit unless the key has already reached the limit in its current window.
     *
     * @return {@code true} if the hit was counted, {@code false} if the key is limited
     */
    public boolean tryIncrement(String key) {
        Instant now = clock.instant();
        boolean[] counted = {false};
        windows.compute(key, (k, current) -> {
            Window active = activeOrNull(current, now);
            if (active == null) {
                counted[0] = true;
                return new Window(now, 1);
            }
            if (active.count() >= limit) {
                return active;
            }
            counted[0] = true;
            return new Window(active.start(), active.count() + 1);
        });
        enforceMaxKeys(now);
        return counted[0];
    }

    /** Counts one hit, never exceeding the limit. */
    public void increment(String key) {
        tryIncrement(key);
    }

    /** Whether the key has reached the limit in its current window. */
    public boolean isLimited(String key) {
        Window active = activeOrNull(windows.get(key), clock.instant());
        return active != null && active.count() >= limit;
    }

    /** Time until the key's current window resets; at least one second. */
    public Duration retryAfter(String key) {
        Instant now = clock.instant();
        Window active = activeOrNull(windows.get(key), now);
        if (active == null) {
            return MIN_RETRY_AFTER;
        }
        Duration remaining = Duration.between(now, active.start().plus(window));
        return remaining.compareTo(MIN_RETRY_AFTER) < 0 ? MIN_RETRY_AFTER : remaining;
    }

    /** Forgets the key's window. */
    public void reset(String key) {
        windows.remove(key);
    }

    /** Number of keys currently tracked (for tests and diagnostics). */
    int size() {
        return windows.size();
    }

    private Window activeOrNull(Window current, Instant now) {
        if (current == null || !now.isBefore(current.start().plus(window))) {
            return null;
        }
        return current;
    }

    private void enforceMaxKeys(Instant now) {
        if (windows.size() <= maxKeys) {
            return;
        }
        windows.entrySet().removeIf(e -> activeOrNull(e.getValue(), now) == null);
        if (windows.size() > maxKeys) {
            log.warn("Throttling counter exceeded {} tracked keys; clearing all windows to bound memory", maxKeys);
            windows.clear();
        }
    }
}
