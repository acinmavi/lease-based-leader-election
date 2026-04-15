package io.github.leaseelection.core;

/**
 * Explicit leadership lifecycle states make failover behavior easier to reason about
 * than a boolean leader flag.
 */
public enum ElectionState {
    STARTING,
    FOLLOWER,
    CONTENDER,
    LEADER,
    LOST_LEADERSHIP,
    GIVING_UP,
    SHUTDOWN
}
