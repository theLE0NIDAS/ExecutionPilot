package com.executionpilot.workflow.domain;

import java.util.Objects;

public final class TransitionDefinition {

    private final String transitionId;
    private final String fromStateId;
    private final String toStateId;
    private final String eventName;
    private final String guardExpression;

    public TransitionDefinition(
            String transitionId,
            String fromStateId,
            String toStateId,
            String eventName,
            String guardExpression
    ) {
        this.transitionId = requireText(transitionId, "transitionId");
        this.fromStateId = requireText(fromStateId, "fromStateId");
        this.toStateId = requireText(toStateId, "toStateId");
        this.eventName = requireText(eventName, "eventName");
        this.guardExpression = guardExpression == null ? "" : guardExpression;
    }

    public String getTransitionId() {
        return transitionId;
    }

    public String getFromStateId() {
        return fromStateId;
    }

    public String getToStateId() {
        return toStateId;
    }

    public String getEventName() {
        return eventName;
    }

    public String getGuardExpression() {
        return guardExpression;
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
