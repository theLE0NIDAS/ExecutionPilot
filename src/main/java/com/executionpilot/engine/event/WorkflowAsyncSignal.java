package com.executionpilot.engine.event;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class WorkflowAsyncSignal {

    private final String eventId;
    private final String executionId;
    private final String workflowId;
    private final int workflowVersion;
    private final String stateId;
    private final AsyncWorkflowSignalType signalType;
    private final String message;
    private final Map<String, Object> payload;
    private final Instant occurredAt;

    public WorkflowAsyncSignal(
            String eventId,
            String executionId,
            String workflowId,
            int workflowVersion,
            String stateId,
            AsyncWorkflowSignalType signalType,
            String message,
            Map<String, Object> payload,
            Instant occurredAt
    ) {
        this.eventId = requireText(eventId, "eventId");
        this.executionId = requireText(executionId, "executionId");
        this.workflowId = requireText(workflowId, "workflowId");
        if (workflowVersion < 1) {
            throw new IllegalArgumentException("workflowVersion must be greater than 0");
        }
        this.workflowVersion = workflowVersion;
        this.stateId = requireText(stateId, "stateId");
        this.signalType = Objects.requireNonNull(signalType, "signalType must not be null");
        this.message = requireText(message, "message");
        this.payload = payload == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(payload));
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    public static WorkflowAsyncSignal create(
            String executionId,
            String workflowId,
            int workflowVersion,
            String stateId,
            AsyncWorkflowSignalType signalType,
            String message,
            Map<String, Object> payload
    ) {
        return new WorkflowAsyncSignal(
                UUID.randomUUID().toString(),
                executionId,
                workflowId,
                workflowVersion,
                stateId,
                signalType,
                message,
                payload,
                Instant.now()
        );
    }

    public String getEventId() {
        return eventId;
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

    public String getStateId() {
        return stateId;
    }

    public AsyncWorkflowSignalType getSignalType() {
        return signalType;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
