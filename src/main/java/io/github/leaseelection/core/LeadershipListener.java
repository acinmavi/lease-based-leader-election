package io.github.leaseelection.core;

public interface LeadershipListener {
    void onStateChange(String nodeId, ElectionState from, ElectionState to, String reason);

    LeadershipListener NO_OP = (nodeId, from, to, reason) -> { };
}
