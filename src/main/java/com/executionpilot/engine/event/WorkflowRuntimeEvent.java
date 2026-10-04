package com.executionpilot.engine.event;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class WorkflowRuntimeEvent {

    private final String executionId;
    private final String workflowId;
    private final String stateId;
    private final WorkflowEventType eventType;
    private final String message;
    private final Map<String, Object> payload;
    private final Instant occurredAt;

    public WorkflowRuntimeEvent(
            String executionId,
            String workflowId,
            String stateId,
            WorkflowEventType eventType,
            String message,
            Map<String, Object> payload,
            Instant occurredAt
    ) {
        this.executionId = requireText(executionId, "executionId");
        this.workflowId = requireText(workflowId, "workflowId");
        this.stateId = requireText(stateId, "stateId");
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.message = requireText(message, "message");
        this.payload = payload == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(payload));
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
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

    public WorkflowEventType getEventType() {
        return eventType;
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
