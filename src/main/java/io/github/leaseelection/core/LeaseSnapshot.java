package io.github.leaseelection.core;

import java.time.Instant;
import java.util.Objects;

public final class LeaseSnapshot {
    private final String lockName;
    private final String ownerId;
    private final long version;
    private final Instant acquiredAt;
    private final Instant expiresAt;

    public LeaseSnapshot(String lockName, String ownerId, long version, Instant acquiredAt, Instant expiresAt) {
        this.lockName = Objects.requireNonNull(lockName, "lockName");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.version = version;
        this.acquiredAt = Objects.requireNonNull(acquiredAt, "acquiredAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public String getLockName() {
        return lockName;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public long getVersion() {
        return version;
    }

    public Instant getAcquiredAt() {
        return acquiredAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    @Override
    public String toString() {
        return "LeaseSnapshot{"
                + "lockName='" + lockName + '\''
                + ", ownerId='" + ownerId + '\''
                + ", version=" + version
                + ", expiresAt=" + expiresAt
                + '}';
    }
}
