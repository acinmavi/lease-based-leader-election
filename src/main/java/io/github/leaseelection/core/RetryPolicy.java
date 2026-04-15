package io.github.leaseelection.core;

import java.time.Duration;

public final class RetryPolicy {
    private final Duration initialDelay;
    private final Duration maxDelay;

    private RetryPolicy(Duration initialDelay, Duration maxDelay) {
        this.initialDelay = initialDelay;
        this.maxDelay = maxDelay;
    }

    public static RetryPolicy fixed(Duration initialDelay, Duration maxDelay) {
        return new RetryPolicy(initialDelay, maxDelay);
    }

    public Duration delayForAttempt(int attempt) {
        long candidate = initialDelay.toMillis() * Math.max(1, attempt);
        return Duration.ofMillis(Math.min(candidate, maxDelay.toMillis()));
    }
}
