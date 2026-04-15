package io.github.leaseelection.demo;

import io.github.leaseelection.core.ElectionConfig;
import io.github.leaseelection.core.ElectionState;
import io.github.leaseelection.core.InMemoryLockStore;
import io.github.leaseelection.core.LeaderElectionCoordinator;
import io.github.leaseelection.core.LeadershipListener;
import io.github.leaseelection.core.RetryPolicy;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

public final class LeaderElectionDemo {

    private LeaderElectionDemo() {
    }

    public static void main(String[] args) throws Exception {
        InMemoryLockStore store = new InMemoryLockStore();
        ElectionConfig config = ElectionConfig.builder()
                .lockName("cluster-leader")
                .leaseDuration(Duration.ofSeconds(2))
                .heartbeatInterval(Duration.ofMillis(600))
                .acquireRetryDelay(Duration.ofMillis(350))
                .maxRenewFailures(2)
                .build();

        LeadershipListener listener = (nodeId, from, to, reason) ->
                System.out.printf("[%s] %s -> %s (%s)%n", nodeId, from, to, reason);

        LeaderElectionCoordinator nodeA = new LeaderElectionCoordinator(
                "node-a", store, config, RetryPolicy.fixed(Duration.ofMillis(250), Duration.ofSeconds(1)), listener);
        LeaderElectionCoordinator nodeB = new LeaderElectionCoordinator(
                "node-b", store, config, RetryPolicy.fixed(Duration.ofMillis(250), Duration.ofSeconds(1)), listener);
        LeaderElectionCoordinator nodeC = new LeaderElectionCoordinator(
                "node-c", store, config, RetryPolicy.fixed(Duration.ofMillis(250), Duration.ofSeconds(1)), listener);

        List<LeaderElectionCoordinator> coordinators = Arrays.asList(nodeA, nodeB, nodeC);
        coordinators.forEach(LeaderElectionCoordinator::start);

        Thread.sleep(4_500L);

        LeaderElectionCoordinator leader = coordinators.stream()
                .filter(LeaderElectionCoordinator::isLeader)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No leader elected"));
        System.out.printf("%nStopping leader %s to trigger failover...%n%n", leader.getNodeId());
        leader.close();

        Thread.sleep(5_000L);

        coordinators.stream()
                .filter(coordinator -> coordinator.getState() != ElectionState.SHUTDOWN)
                .forEach(LeaderElectionCoordinator::close);
    }
}
