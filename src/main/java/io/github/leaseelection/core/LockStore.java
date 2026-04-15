package io.github.leaseelection.core;

import java.time.Duration;
import java.util.Optional;

public interface LockStore {
    LeaseOperation tryAcquire(String lockName, String ownerId, Duration leaseDuration);

    LeaseOperation renew(String lockName, String ownerId, Duration leaseDuration);

    boolean release(String lockName, String ownerId);

    Optional<LeaseSnapshot> getCurrentLease(String lockName);
}
