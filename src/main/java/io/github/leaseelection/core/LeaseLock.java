package io.github.leaseelection.core;

import java.time.Duration;
import java.util.Optional;

public interface LeaseLock {
    LeaseOperation acquire(Duration leaseDuration);

    LeaseOperation renew(Duration leaseDuration);

    boolean release();

    Optional<LeaseSnapshot> currentLease();
}
