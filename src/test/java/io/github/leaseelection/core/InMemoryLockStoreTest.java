package io.github.leaseelection.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class InMemoryLockStoreTest {

    @Test
    void shouldAcquireRenewAndTransferExpiredLease() throws Exception {
        InMemoryLockStore store = new InMemoryLockStore();
        Duration leaseTtl = Duration.ofMillis(120);

        LeaseOperation firstAcquire = store.tryAcquire("election", "node-a", leaseTtl);
        assertTrue(firstAcquire.isAcquired());
        assertEquals("node-a", firstAcquire.getLease().getOwnerId());

        LeaseOperation blockedAcquire = store.tryAcquire("election", "node-b", leaseTtl);
        assertFalse(blockedAcquire.isAcquired());
        assertEquals("node-a", blockedAcquire.getLease().getOwnerId());

        LeaseOperation renew = store.renew("election", "node-a", leaseTtl);
        assertTrue(renew.isAcquired());

        Thread.sleep(leaseTtl.toMillis() + 60);

        LeaseOperation takeover = store.tryAcquire("election", "node-b", leaseTtl);
        assertTrue(takeover.isAcquired());
        assertEquals("node-b", takeover.getLease().getOwnerId());
        assertTrue(takeover.getLease().getVersion() > firstAcquire.getLease().getVersion());
    }

    @Test
    void shouldRejectRenewFromNonOwner() {
        InMemoryLockStore store = new InMemoryLockStore();
        Duration leaseTtl = Duration.ofMillis(150);

        store.tryAcquire("election", "node-a", leaseTtl);
        LeaseOperation rejected = store.renew("election", "node-b", leaseTtl);

        assertFalse(rejected.isAcquired());
        assertEquals("node-a", rejected.getLease().getOwnerId());
    }
}
