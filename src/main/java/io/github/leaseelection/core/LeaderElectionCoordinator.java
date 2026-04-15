package io.github.leaseelection.core;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The coordinator has one responsibility: compete for a lease, keep it alive while healthy,
 * and step down fast when renewal fails or the process shuts down.
 */
public final class LeaderElectionCoordinator implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(LeaderElectionCoordinator.class);

    private final String nodeId;
    private final LeaseLock leaseLock;
    private final ElectionConfig config;
    private final RetryPolicy retryPolicy;
    private final LeadershipListener leadershipListener;
    private final ScheduledExecutorService executor;
    private final AtomicReference<ElectionState> state = new AtomicReference<>(ElectionState.STARTING);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger acquireAttempts = new AtomicInteger(0);
    private final AtomicInteger renewFailures = new AtomicInteger(0);

    private volatile ScheduledFuture<?> scheduledTask;

    public LeaderElectionCoordinator(
            String nodeId,
            LockStore lockStore,
            ElectionConfig config,
            RetryPolicy retryPolicy,
            LeadershipListener leadershipListener) {
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId");
        this.config = Objects.requireNonNull(config, "config");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
        this.leadershipListener = leadershipListener == null ? LeadershipListener.NO_OP : leadershipListener;
        this.leaseLock = new StoreBackedLeaseLock(config.getLockName(), nodeId, Objects.requireNonNull(lockStore, "lockStore"));
        this.executor = Executors.newSingleThreadScheduledExecutor(new CoordinatorThreadFactory(nodeId));
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        transitionTo(ElectionState.FOLLOWER, "coordinator-started");
        scheduleAcquire(config.getAcquireRetryDelay());
    }

    public String getNodeId() {
        return nodeId;
    }

    public ElectionState getState() {
        return state.get();
    }

    public Optional<LeaseSnapshot> currentLease() {
        return leaseLock.currentLease();
    }

    public boolean isLeader() {
        return state.get() == ElectionState.LEADER;
    }

    private void scheduleAcquire(Duration delay) {
        schedule(delay, this::attemptAcquire);
    }

    private void attemptAcquire() {
        if (!running.get()) {
            return;
        }
        transitionTo(ElectionState.CONTENDER, "attempting-acquire");
        LeaseOperation operation = leaseLock.acquire(config.getLeaseDuration());
        if (operation.isAcquired()) {
            acquireAttempts.set(0);
            renewFailures.set(0);
            transitionTo(ElectionState.LEADER, "lease-acquired");
            log.info("node={} acquired leadership version={} expiresAt={}",
                    nodeId, operation.getLease().getVersion(), operation.getLease().getExpiresAt());
            schedule(config.getHeartbeatInterval(), this::heartbeat);
            return;
        }
        int attempt = acquireAttempts.incrementAndGet();
        transitionTo(ElectionState.FOLLOWER, operation.getDetail());
        Duration nextDelay = retryPolicy.delayForAttempt(attempt);
        log.info("node={} remains follower owner={} retryIn={}ms",
                nodeId, operation.getLease().getOwnerId(), nextDelay.toMillis());
        scheduleAcquire(nextDelay.plus(config.getAcquireRetryDelay()));
    }

    private void heartbeat() {
        if (!running.get()) {
            return;
        }
        LeaseOperation operation = leaseLock.renew(config.getLeaseDuration());
        if (operation.isAcquired()) {
            renewFailures.set(0);
            log.info("node={} renewed lease version={} expiresAt={}",
                    nodeId, operation.getLease().getVersion(), operation.getLease().getExpiresAt());
            schedule(config.getHeartbeatInterval(), this::heartbeat);
            return;
        }

        int failureCount = renewFailures.incrementAndGet();
        log.warn("node={} failed to renew lease detail={} observedOwner={} failures={}",
                nodeId, operation.getDetail(), operation.getLease().getOwnerId(), failureCount);
        if (failureCount >= config.getMaxRenewFailures()) {
            handleLeadershipLoss("renew-failed");
            return;
        }
        schedule(retryPolicy.delayForAttempt(failureCount), this::heartbeat);
    }

    private void handleLeadershipLoss(String reason) {
        transitionTo(ElectionState.LOST_LEADERSHIP, reason);
        renewFailures.set(0);
        acquireAttempts.set(0);
        transitionTo(ElectionState.FOLLOWER, "retry-after-loss");
        scheduleAcquire(config.getAcquireRetryDelay());
    }

    private synchronized void schedule(Duration delay, Runnable task) {
        if (!running.get()) {
            return;
        }
        if (scheduledTask != null && !scheduledTask.isDone()) {
            scheduledTask.cancel(false);
        }
        scheduledTask = executor.schedule(task, Math.max(0L, delay.toMillis()), TimeUnit.MILLISECONDS);
    }

    private void transitionTo(ElectionState targetState, String reason) {
        ElectionState previous = state.getAndSet(targetState);
        if (previous != targetState) {
            leadershipListener.onStateChange(nodeId, previous, targetState, reason);
            log.info("node={} transition {} -> {} reason={}", nodeId, previous, targetState, reason);
        }
    }

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        ScheduledFuture<?> currentTask = scheduledTask;
        if (currentTask != null) {
            currentTask.cancel(false);
        }
        if (state.get() == ElectionState.LEADER) {
            transitionTo(ElectionState.GIVING_UP, "shutdown-requested");
            leaseLock.release();
        }
        transitionTo(ElectionState.SHUTDOWN, "coordinator-stopped");
        executor.shutdownNow();
    }

    private static final class CoordinatorThreadFactory implements ThreadFactory {
        private final String nodeId;

        private CoordinatorThreadFactory(String nodeId) {
            this.nodeId = nodeId;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "leader-election-" + nodeId);
            thread.setDaemon(true);
            return thread;
        }
    }
}
