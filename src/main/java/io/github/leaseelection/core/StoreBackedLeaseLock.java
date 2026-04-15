package io.github.leaseelection.core;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public final class StoreBackedLeaseLock implements LeaseLock {
    private final String lockName;
    private final String ownerId;
    private final LockStore lockStore;

    public StoreBackedLeaseLock(String lockName, String ownerId, LockStore lockStore) {
        this.lockName = Objects.requireNonNull(lockName, "lockName");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.lockStore = Objects.requireNonNull(lockStore, "lockStore");
    }

    @Override
    public LeaseOperation acquire(Duration leaseDuration) {
        return lockStore.tryAcquire(lockName, ownerId, leaseDuration);
    }

    @Override
    public LeaseOperation renew(Duration leaseDuration) {
        return lockStore.renew(lockName, ownerId, leaseDuration);
    }

    @Override
    public boolean release() {
        return lockStore.release(lockName, ownerId);
    }

    @Override
    public Optional<LeaseSnapshot> currentLease() {
        return lockStore.getCurrentLease(lockName);
    }
}
