package com.executionpilot.engine.event;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class WorkflowEventLogEntry {

    private final String eventId;
    private final String executionId;
    private final String workflowId;
    private final String stateId;
    private final String eventType;
    private final String message;
    private final Map<String, Object> payload;
    private final Instant createdAt;

    public WorkflowEventLogEntry(
            String eventId,
            String executionId,
            String workflowId,
            String stateId,
            String eventType,
            String message,
            Map<String, Object> payload,
            Instant createdAt
    ) {
        this.eventId = requireText(eventId, "eventId");
        this.executionId = requireText(executionId, "executionId");
        this.workflowId = requireText(workflowId, "workflowId");
        this.stateId = requireText(stateId, "stateId");
        this.eventType = requireText(eventType, "eventType");
        this.message = requireText(message, "message");
        this.payload = payload == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(payload));
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
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

    public String getStateId() {
        return stateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
