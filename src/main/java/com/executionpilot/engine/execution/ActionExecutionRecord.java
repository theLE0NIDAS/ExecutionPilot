package com.executionpilot.engine.execution;

import java.time.Instant;
import java.util.Objects;

public final class ActionExecutionRecord {

    private final String actionExecutionId;
    private final String executionId;
    private final String workflowId;
    private final String stateId;
    private final String actionId;
    private final int attemptNo;
    private final ActionExecutionStatus status;
    private final Object input;
    private final Object output;
    private final String errorMessage;
    private final Instant startedAt;
    private final Instant finishedAt;

    public ActionExecutionRecord(
            String actionExecutionId,
            String executionId,
            String workflowId,
            String stateId,
            String actionId,
            int attemptNo,
            ActionExecutionStatus status,
            Object input,
            Object output,
            String errorMessage,
            Instant startedAt,
            Instant finishedAt
    ) {
        this.actionExecutionId = requireText(actionExecutionId, "actionExecutionId");
        this.executionId = requireText(executionId, "executionId");
        this.workflowId = requireText(workflowId, "workflowId");
        this.stateId = requireText(stateId, "stateId");
        this.actionId = requireText(actionId, "actionId");
        if (attemptNo < 1) {
            throw new IllegalArgumentException("attemptNo must be at least 1");
        }
        this.attemptNo = attemptNo;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.input = input;
        this.output = output;
        this.errorMessage = errorMessage == null ? "" : errorMessage;
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
        this.finishedAt = Objects.requireNonNull(finishedAt, "finishedAt must not be null");
        if (finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("finishedAt must not be before startedAt");
        }
    }

    public String getActionExecutionId() {
        return actionExecutionId;
    }

    public String getExecutionId() {
        return executionId;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public String getStateId() {
        return stateId;
    }

    public String getActionId() {
        return actionId;
    }

    public int getAttemptNo() {
        return attemptNo;
    }

    public ActionExecutionStatus getStatus() {
        return status;
    }

    public Object getInput() {
        return input;
    }

    public Object getOutput() {
        return output;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
