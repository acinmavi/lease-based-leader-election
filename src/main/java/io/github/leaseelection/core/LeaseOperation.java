package io.github.leaseelection.core;

import java.util.Objects;

public final class LeaseOperation {
    private final boolean acquired;
    private final LeaseSnapshot lease;
    private final String detail;

    private LeaseOperation(boolean acquired, LeaseSnapshot lease, String detail) {
        this.acquired = acquired;
        this.lease = Objects.requireNonNull(lease, "lease");
        this.detail = Objects.requireNonNull(detail, "detail");
    }

    public static LeaseOperation acquired(LeaseSnapshot lease, String detail) {
        return new LeaseOperation(true, lease, detail);
    }

    public static LeaseOperation rejected(LeaseSnapshot lease, String detail) {
        return new LeaseOperation(false, lease, detail);
    }

    public boolean isAcquired() {
        return acquired;
    }

    public LeaseSnapshot getLease() {
        return lease;
    }

    public String getDetail() {
        return detail;
    }
}
