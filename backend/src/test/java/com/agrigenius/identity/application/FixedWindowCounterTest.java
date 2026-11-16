package com.agrigenius.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class FixedWindowCounterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-17T00:00:00Z"));

    @Test
    void countsUpToLimitThenBlocks() {
        FixedWindowCounter counter = new FixedWindowCounter(3, Duration.ofMinutes(15), clock, 100);

        assertThat(counter.tryIncrement("k")).isTrue();
        assertThat(counter.tryIncrement("k")).isTrue();
        assertThat(counter.isLimited("k")).isFalse();
        assertThat(counter.tryIncrement("k")).isTrue();

        assertThat(counter.isLimited("k")).isTrue();
        assertThat(counter.tryIncrement("k")).isFalse();
        assertThat(counter.tryIncrement("k")).isFalse();
    }

    @Test
    void windowExpiryStartsAFreshWindow() {
        FixedWindowCounter counter = new FixedWindowCounter(2, Duration.ofMinutes(15), clock, 100);
        counter.increment("k");
        counter.increment("k");
        assertThat(counter.isLimited("k")).isTrue();

        clock.advance(Duration.ofMinutes(15).minusSeconds(1));
        assertThat(counter.isLimited("k")).isTrue();

        clock.advance(Duration.ofSeconds(1));
        assertThat(counter.isLimited("k")).isFalse();
        assertThat(counter.tryIncrement("k")).isTrue();
        assertThat(counter.isLimited("k")).isFalse();
    }

    @Test
    void windowStartsAtFirstHitNotAtLaterHits() {
        FixedWindowCounter counter = new FixedWindowCounter(2, Duration.ofMinutes(10), clock, 100);
        counter.increment("k");
        clock.advance(Duration.ofMinutes(9));
        counter.increment("k");
        assertThat(counter.isLimited("k")).isTrue();

        clock.advance(Duration.ofMinutes(1));
        assertThat(counter.isLimited("k")).isFalse();
    }

    @Test
    void retryAfterIsTimeUntilWindowResetsAndAtLeastOneSecond() {
        FixedWindowCounter counter = new FixedWindowCounter(1, Duration.ofMinutes(15), clock, 100);
        assertThat(counter.retryAfter("unknown")).isEqualTo(Duration.ofSeconds(1));

        counter.increment("k");
        assertThat(counter.retryAfter("k")).isEqualTo(Duration.ofMinutes(15));

        clock.advance(Duration.ofMinutes(10));
        assertThat(counter.retryAfter("k")).isEqualTo(Duration.ofMinutes(5));

        clock.advance(Duration.ofMinutes(5).minusMillis(200));
        assertThat(counter.retryAfter("k")).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void resetClearsTheKey() {
        FixedWindowCounter counter = new FixedWindowCounter(1, Duration.ofMinutes(15), clock, 100);
        counter.increment("k");
        assertThat(counter.isLimited("k")).isTrue();

        counter.reset("k");

        assertThat(counter.isLimited("k")).isFalse();
        assertThat(counter.tryIncrement("k")).isTrue();
    }

    @Test
    void keysAreIndependent() {
        FixedWindowCounter counter = new FixedWindowCounter(1, Duration.ofMinutes(15), clock, 100);
        counter.increment("a");

        assertThat(counter.isLimited("a")).isTrue();
        assertThat(counter.isLimited("b")).isFalse();
        assertThat(counter.tryIncrement("b")).isTrue();
    }

    @Test
    void expiredKeysArePurgedWhenOverMaxKeys() {
        FixedWindowCounter counter = new FixedWindowCounter(5, Duration.ofMinutes(15), clock, 3);
        counter.increment("old-1");
        counter.increment("old-2");
        counter.increment("old-3");
        clock.advance(Duration.ofMinutes(16));

        counter.increment("new-1");

        assertThat(counter.size()).isEqualTo(1);
        assertThat(counter.isLimited("new-1")).isFalse();
    }

    @Test
    void mapIsClearedWhenStillOverMaxKeysAfterPurge() {
        FixedWindowCounter counter = new FixedWindowCounter(5, Duration.ofMinutes(15), clock, 3);
        counter.increment("a");
        counter.increment("b");
        counter.increment("c");
        assertThat(counter.size()).isEqualTo(3);

        counter.increment("d");

        assertThat(counter.size()).isZero();
    }

    @Test
    void concurrentIncrementsNeverExceedLimit() throws Exception {
        int limit = 50;
        FixedWindowCounter counter = new FixedWindowCounter(limit, Duration.ofMinutes(15), clock, 1000);
        int threads = 16;
        int attemptsPerThread = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                results.add(pool.submit(() -> {
                    start.await();
                    int accepted = 0;
                    for (int i = 0; i < attemptsPerThread; i++) {
                        if (counter.tryIncrement("shared")) {
                            accepted++;
                        }
                    }
                    return accepted;
                }));
            }
            start.countDown();
            int totalAccepted = 0;
            for (Future<Integer> result : results) {
                totalAccepted += result.get(10, TimeUnit.SECONDS);
            }
            assertThat(totalAccepted).isEqualTo(limit);
            assertThat(counter.isLimited("shared")).isTrue();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new FixedWindowCounter(0, Duration.ofMinutes(1), clock, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FixedWindowCounter(1, Duration.ZERO, clock, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FixedWindowCounter(1, Duration.ofMinutes(1), clock, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
