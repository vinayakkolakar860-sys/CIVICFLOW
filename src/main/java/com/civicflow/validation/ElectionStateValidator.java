package com.civicflow.validation;

import com.civicflow.exception.ValidationException;
import com.civicflow.model.ElectionStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Enforces valid election lifecycle state transitions.
 * Lifecycle: DRAFT -> SCHEDULED -> OPEN -> CLOSED -> PUBLISHED (with controlled CANCELLED exits).
 */
public final class ElectionStateValidator {

    private static final Map<ElectionStatus, Set<ElectionStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(ElectionStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(ElectionStatus.DRAFT, EnumSet.of(ElectionStatus.SCHEDULED, ElectionStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(ElectionStatus.SCHEDULED, EnumSet.of(ElectionStatus.OPEN, ElectionStatus.DRAFT, ElectionStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(ElectionStatus.OPEN, EnumSet.of(ElectionStatus.CLOSED, ElectionStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(ElectionStatus.CLOSED, EnumSet.of(ElectionStatus.PUBLISHED));
        ALLOWED_TRANSITIONS.put(ElectionStatus.PUBLISHED, EnumSet.noneOf(ElectionStatus.class));
        ALLOWED_TRANSITIONS.put(ElectionStatus.CANCELLED, EnumSet.noneOf(ElectionStatus.class));
    }

    private ElectionStateValidator() {
    }

    public static boolean isTransitionAllowed(ElectionStatus from, ElectionStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return ALLOWED_TRANSITIONS.getOrDefault(from, EnumSet.noneOf(ElectionStatus.class)).contains(to);
    }

    public static void validateTransition(ElectionStatus from, ElectionStatus to) {
        if (!isTransitionAllowed(from, to)) {
            throw new ValidationException(
                    "Invalid election status transition from " + from + " to " + to + ".");
        }
    }

    public static void ensureEditable(ElectionStatus status) {
        if (status == ElectionStatus.PUBLISHED) {
            throw new ValidationException("Published elections are immutable and cannot be modified.");
        }
        if (status == ElectionStatus.CLOSED || status == ElectionStatus.CANCELLED) {
            throw new ValidationException("Elections in " + status + " state cannot be edited.");
        }
        if (status == ElectionStatus.OPEN) {
            throw new ValidationException("An active OPEN election cannot have its core configuration altered.");
        }
    }
}
