package com.executionpilot.engine.state;

import com.executionpilot.engine.execution.ExecutionStatus;

import java.util.Objects;

public final class StateTransitionDecision {

    private final String nextStateId;
    private final ExecutionStatus status;
    private final boolean terminal;
    private final String transitionId;
    private final String message;

    private StateTransitionDecision(
            String nextStateId,
            ExecutionStatus status,
            boolean terminal,
            String transitionId,
            String message
    ) {
        this.nextStateId = requireText(nextStateId, "nextStateId");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.terminal = terminal;
        this.transitionId = transitionId == null ? "" : transitionId;
        this.message = requireText(message, "message");
    }

    public static StateTransitionDecision advance(String nextStateId, String transitionId, String message) {
        return new StateTransitionDecision(nextStateId, ExecutionStatus.RUNNING, false, transitionId, message);
    }

    public static StateTransitionDecision complete(String stateId, String message) {
        return new StateTransitionDecision(stateId, ExecutionStatus.COMPLETED, true, "", message);
    }

    public static StateTransitionDecision fail(String stateId, String message) {
        return new StateTransitionDecision(stateId, ExecutionStatus.FAILED, true, "", message);
    }

    public String getNextStateId() {
        return nextStateId;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public String getTransitionId() {
        return transitionId;
    }

    public String getMessage() {
        return message;
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
