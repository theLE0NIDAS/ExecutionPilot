package com.executionpilot.engine.execution;

import java.time.Instant;
import java.util.Objects;

public final class WorkflowExecution {

    private final String executionId;
    private final String workflowId;
    private final int workflowVersion;
    private final ExecutionStatus status;
    private final String currentStateId;
    private final WorkflowContext context;
    private final Instant startedAt;
    private final Instant updatedAt;

    public WorkflowExecution(
            String executionId,
            String workflowId,
            int workflowVersion,
            ExecutionStatus status,
            String currentStateId,
            WorkflowContext context,
            Instant startedAt,
            Instant updatedAt
    ) {
        this.executionId = requireText(executionId, "executionId");
        this.workflowId = requireText(workflowId, "workflowId");
        if (workflowVersion < 1) {
            throw new IllegalArgumentException("workflowVersion must be greater than 0");
        }
        this.workflowVersion = workflowVersion;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.currentStateId = requireText(currentStateId, "currentStateId");
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (updatedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("updatedAt must not be before startedAt");
        }
    }

    public String getExecutionId() {
        return executionId;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public int getWorkflowVersion() {
        return workflowVersion;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public String getCurrentStateId() {
        return currentStateId;
    }

    public WorkflowContext getContext() {
        return context;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
