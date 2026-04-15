package io.github.leaseelection.core;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class LeaderElectionCoordinatorTest {

    @Test
    void shouldFailOverWhenLeaderStopsRenewing() {
        InMemoryLockStore store = new InMemoryLockStore();
        ElectionConfig config = ElectionConfig.builder()
                .lockName("demo-cluster")
                .leaseDuration(Duration.ofMillis(280))
                .heartbeatInterval(Duration.ofMillis(90))
                .acquireRetryDelay(Duration.ofMillis(70))
                .maxRenewFailures(2)
                .build();

        RecordingListener firstListener = new RecordingListener();
        RecordingListener secondListener = new RecordingListener();

        LeaderElectionCoordinator first = new LeaderElectionCoordinator(
                "node-a", store, config, RetryPolicy.fixed(Duration.ofMillis(70), Duration.ofMillis(140)), firstListener);
        LeaderElectionCoordinator second = new LeaderElectionCoordinator(
                "node-b", store, config, RetryPolicy.fixed(Duration.ofMillis(70), Duration.ofMillis(140)), secondListener);

        first.start();
        second.start();

        await().atMost(2, TimeUnit.SECONDS).until(() -> firstListener.hasState(ElectionState.LEADER)
                || secondListener.hasState(ElectionState.LEADER));

        LeaderElectionCoordinator leader = firstListener.hasState(ElectionState.LEADER) ? first : second;
        RecordingListener followerListener = leader == first ? secondListener : firstListener;

        leader.close();

        await().atMost(3, TimeUnit.SECONDS).until(() -> followerListener.hasState(ElectionState.LEADER));

        first.close();
        second.close();

        assertTrue(followerListener.hasState(ElectionState.LEADER));
    }

    @Test
    void shouldReportLeadershipLossWhenRenewKeepsFailing() {
        LockStore unstableStore = new LockStore() {
            private final InMemoryLockStore delegate = new InMemoryLockStore();
            private volatile boolean failRenew = false;

            @Override
            public LeaseOperation tryAcquire(String lockName, String ownerId, Duration leaseDuration) {
                LeaseOperation acquired = delegate.tryAcquire(lockName, ownerId, leaseDuration);
                if (acquired.isAcquired()) {
                    failRenew = true;
                }
                return acquired;
            }

            @Override
            public LeaseOperation renew(String lockName, String ownerId, Duration leaseDuration) {
                if (failRenew) {
                    LeaseSnapshot foreignLease = new LeaseSnapshot(
                            lockName,
                            "other-node",
                            999L,
                            java.time.Instant.now(),
                            java.time.Instant.now().plusMillis(200));
                    return LeaseOperation.rejected(foreignLease, "simulated-renew-failure");
                }
                return delegate.renew(lockName, ownerId, leaseDuration);
            }

            @Override
            public boolean release(String lockName, String ownerId) {
                return delegate.release(lockName, ownerId);
            }

            @Override
            public java.util.Optional<LeaseSnapshot> getCurrentLease(String lockName) {
                return delegate.getCurrentLease(lockName);
            }
        };

        RecordingListener listener = new RecordingListener();
        LeaderElectionCoordinator coordinator = new LeaderElectionCoordinator(
                "node-a",
                unstableStore,
                ElectionConfig.builder()
                        .lockName("demo-cluster")
                        .leaseDuration(Duration.ofMillis(260))
                        .heartbeatInterval(Duration.ofMillis(60))
                        .acquireRetryDelay(Duration.ofMillis(50))
                        .maxRenewFailures(1)
                        .build(),
                RetryPolicy.fixed(Duration.ofMillis(40), Duration.ofMillis(100)),
                listener);

        coordinator.start();

        await().atMost(2, TimeUnit.SECONDS).until(() -> listener.hasState(ElectionState.LOST_LEADERSHIP));
        await().atMost(2, TimeUnit.SECONDS).until(() -> listener.hasState(ElectionState.FOLLOWER));

        coordinator.close();
        assertTrue(listener.hasState(ElectionState.LOST_LEADERSHIP));
    }

    private static final class RecordingListener implements LeadershipListener {
        private final List<ElectionState> states = new CopyOnWriteArrayList<>();
        private final List<String> events = new ArrayList<>();

        @Override
        public void onStateChange(String nodeId, ElectionState from, ElectionState to, String reason) {
            states.add(to);
            synchronized (events) {
                events.add(nodeId + ":" + from + "->" + to + ":" + reason);
            }
        }

        private boolean hasState(ElectionState state) {
            return states.contains(state);
        }
    }
}
