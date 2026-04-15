package io.github.leaseelection.core;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This store keeps only the lease semantics needed for the demo.
 * Production systems would back this contract with a CP data store or consensus service.
 */
public final class InMemoryLockStore implements LockStore {
    private final ConcurrentMap<String, LeaseSnapshot> leases = new ConcurrentHashMap<>();
    private final AtomicLong versions = new AtomicLong(0L);

    @Override
    public LeaseOperation tryAcquire(String lockName, String ownerId, Duration leaseDuration) {
        Instant now = Instant.now();
        LeaseSnapshot updated = leases.compute(lockName, (name, current) -> {
            if (current == null || current.isExpired(now) || ownerId.equals(current.getOwnerId())) {
                return nextSnapshot(lockName, ownerId, leaseDuration, now);
            }
            return current;
        });
        if (ownerId.equals(updated.getOwnerId())) {
            return LeaseOperation.acquired(updated, "lease-acquired");
        }
        return LeaseOperation.rejected(updated, "lease-held-by-peer");
    }

    @Override
    public LeaseOperation renew(String lockName, String ownerId, Duration leaseDuration) {
        Instant now = Instant.now();
        LeaseSnapshot updated = leases.compute(lockName, (name, current) -> {
            if (current == null) {
                return null;
            }
            if (current.isExpired(now)) {
                return current;
            }
            if (!ownerId.equals(current.getOwnerId())) {
                return current;
            }
            return new LeaseSnapshot(
                    lockName,
                    ownerId,
                    versions.incrementAndGet(),
                    current.getAcquiredAt(),
                    now.plus(leaseDuration));
        });
        if (updated == null) {
            return LeaseOperation.rejected(
                    new LeaseSnapshot(lockName, "none", versions.incrementAndGet(), now, now),
                    "lease-missing");
        }
        if (ownerId.equals(updated.getOwnerId()) && !updated.isExpired(now)) {
            return LeaseOperation.acquired(updated, "lease-renewed");
        }
        return LeaseOperation.rejected(updated, "lease-renew-rejected");
    }

    @Override
    public boolean release(String lockName, String ownerId) {
        return leases.computeIfPresent(lockName, (name, current) -> ownerId.equals(current.getOwnerId()) ? null : current) == null;
    }

    @Override
    public Optional<LeaseSnapshot> getCurrentLease(String lockName) {
        LeaseSnapshot current = leases.get(lockName);
        if (current == null) {
            return Optional.empty();
        }
        if (current.isExpired(Instant.now())) {
            leases.remove(lockName, current);
            return Optional.empty();
        }
        return Optional.of(current);
    }

    private LeaseSnapshot nextSnapshot(String lockName, String ownerId, Duration leaseDuration, Instant now) {
        return new LeaseSnapshot(lockName, ownerId, versions.incrementAndGet(), now, now.plus(leaseDuration));
    }
}
