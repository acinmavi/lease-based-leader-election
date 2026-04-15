package io.github.leaseelection.core;

import java.time.Duration;

public final class ElectionConfig {
    private final String lockName;
    private final Duration leaseDuration;
    private final Duration heartbeatInterval;
    private final Duration acquireRetryDelay;
    private final int maxRenewFailures;

    private ElectionConfig(Builder builder) {
        this.lockName = builder.lockName;
        this.leaseDuration = builder.leaseDuration;
        this.heartbeatInterval = builder.heartbeatInterval;
        this.acquireRetryDelay = builder.acquireRetryDelay;
        this.maxRenewFailures = builder.maxRenewFailures;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getLockName() {
        return lockName;
    }

    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    public Duration getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public Duration getAcquireRetryDelay() {
        return acquireRetryDelay;
    }

    public int getMaxRenewFailures() {
        return maxRenewFailures;
    }

    public static final class Builder {
        private String lockName = "leader-election";
        private Duration leaseDuration = Duration.ofSeconds(3);
        private Duration heartbeatInterval = Duration.ofSeconds(1);
        private Duration acquireRetryDelay = Duration.ofMillis(500);
        private int maxRenewFailures = 3;

        public Builder lockName(String lockName) {
            this.lockName = lockName;
            return this;
        }

        public Builder leaseDuration(Duration leaseDuration) {
            this.leaseDuration = leaseDuration;
            return this;
        }

        public Builder heartbeatInterval(Duration heartbeatInterval) {
            this.heartbeatInterval = heartbeatInterval;
            return this;
        }

        public Builder acquireRetryDelay(Duration acquireRetryDelay) {
            this.acquireRetryDelay = acquireRetryDelay;
            return this;
        }

        public Builder maxRenewFailures(int maxRenewFailures) {
            this.maxRenewFailures = maxRenewFailures;
            return this;
        }

        public ElectionConfig build() {
            return new ElectionConfig(this);
        }
    }
}
