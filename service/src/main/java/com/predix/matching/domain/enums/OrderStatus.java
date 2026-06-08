package com.predix.matching.domain.enums;

import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {
    NEW,
    PARTIAL,
    PENDING_MATCH,
    PENDING_CANCEL,
    FILLED,
    CANCELLED,
    REJECTED;

    private static final Set<OrderStatus> CANCELLABLE =
            EnumSet.of(NEW, PARTIAL, PENDING_MATCH, PENDING_CANCEL);
    private static final Set<OrderStatus> FINAL = EnumSet.of(FILLED, CANCELLED, REJECTED);

    public boolean canCancel() {
        return CANCELLABLE.contains(this);
    }

    public boolean isFinal() {
        return FINAL.contains(this);
    }

    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case NEW -> target == PARTIAL || target == FILLED || target == CANCELLED
                    || target == REJECTED || target == PENDING_MATCH || target == PENDING_CANCEL;
            case PARTIAL -> target == FILLED || target == CANCELLED
                    || target == PENDING_MATCH || target == PENDING_CANCEL;
            case PENDING_MATCH -> target == PARTIAL || target == FILLED || target == CANCELLED
                    || target == PENDING_CANCEL;
            case PENDING_CANCEL -> target == CANCELLED;
            case FILLED, CANCELLED, REJECTED -> false;
        };
    }
}
